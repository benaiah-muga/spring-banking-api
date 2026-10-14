package com.banking.api.service;

import com.banking.api.dto.request.SetSpendingLimitRequest;
import com.banking.api.dto.response.AccountStatementResponse;
import com.banking.api.dto.response.SpendingLimitResponse;
import com.banking.api.entity.Account;
import com.banking.api.entity.Transaction;
import com.banking.api.entity.User;
import com.banking.api.enums.TransactionType;
import com.banking.api.exception.AccessDeniedException;
import com.banking.api.exception.BadRequestException;
import com.banking.api.exception.ResourceNotFoundException;
import com.banking.api.repository.AccountRepository;
import com.banking.api.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatementService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UserService userService;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public AccountStatementResponse generateStatement(Long accountId, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new BadRequestException("Statement end date must be on or after its start date");
        }
        User currentUser = userService.getCurrentUser();
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));

        // Validate ownership (unless admin)
        if (!isAdmin(currentUser) && !account.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You do not have access to this account");
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endExclusive = endDate.plusDays(1).atStartOfDay();

        long transactionCount = transactionRepository.countAccountTransactionsBetween(accountId, startDateTime, endExclusive);
        if (transactionCount > 10_000) {
            throw new BadRequestException("Statement period exceeds 10000 transactions; narrow the date range");
        }
        List<Transaction> periodTransactions = transactionRepository.findAccountTransactionsBetween(
                accountId, startDateTime, endExclusive, PageRequest.of(0, 10_000));

        // Calculate totals
        BigDecimal totalDeposits = BigDecimal.ZERO;
        BigDecimal totalWithdrawals = BigDecimal.ZERO;
        BigDecimal totalTransfersIn = BigDecimal.ZERO;
        BigDecimal totalTransfersOut = BigDecimal.ZERO;

        List<AccountStatementResponse.StatementTransaction> statementTransactions = new java.util.ArrayList<>();

        // First pass: calculate totals
        for (Transaction t : periodTransactions) {
            switch (t.getType()) {
                case DEPOSIT -> totalDeposits = totalDeposits.add(t.getAmount());
                case WITHDRAWAL -> totalWithdrawals = totalWithdrawals.add(t.getAmount());
                case TRANSFER -> {
                    if (t.getSourceAccount().getId().equals(accountId)) {
                        totalTransfersOut = totalTransfersOut.add(t.getAmount());
                    } else {
                        totalTransfersIn = totalTransfersIn.add(t.getAmount());
                    }
                }
            }
        }

        BigDecimal openingBalance = transactionRepository.calculateBalanceBefore(
                accountId, startDateTime,
                TransactionType.DEPOSIT, TransactionType.WITHDRAWAL, TransactionType.TRANSFER);
        BigDecimal netChange = totalDeposits.add(totalTransfersIn)
                .subtract(totalWithdrawals).subtract(totalTransfersOut);

        BigDecimal runningBalance = openingBalance;
        for (Transaction t : periodTransactions) {
            BigDecimal amount = t.getAmount();
            boolean isCredit = t.getType() == TransactionType.DEPOSIT ||
                    (t.getType() == TransactionType.TRANSFER && t.getDestinationAccount() != null 
                            && t.getDestinationAccount().getId().equals(accountId));
            
            if (isCredit) {
                runningBalance = runningBalance.add(amount);
            } else {
                runningBalance = runningBalance.subtract(amount);
            }

            statementTransactions.add(AccountStatementResponse.StatementTransaction.builder()
                    .date(t.getTimestamp())
                    .referenceNumber(t.getReferenceNumber())
                    .type(t.getType())
                    .description(t.getDescription())
                    .amount(isCredit ? amount : amount.negate())
                    .balanceAfter(runningBalance)
                    .build());
        }

        // Log the statement generation
        auditService.logAccountAction("STATEMENT_GENERATED", accountId, 
                "Statement generated for period: " + startDate + " to " + endDate);

        return AccountStatementResponse.builder()
                .accountId(account.getId())
                .accountNumber(account.getAccountNumber())
                .accountHolderName(account.getUser().getFullName())
                .accountType(account.getAccountType().name())
                .statementPeriodStart(startDate)
                .statementPeriodEnd(endDate)
                .generatedAt(LocalDateTime.now(clock))
                .openingBalance(openingBalance)
                .closingBalance(openingBalance.add(netChange))
                .totalDeposits(totalDeposits)
                .totalWithdrawals(totalWithdrawals)
                .totalTransfersIn(totalTransfersIn)
                .totalTransfersOut(totalTransfersOut)
                .transactionCount(statementTransactions.size())
                .transactions(statementTransactions)
                .build();
    }

    @Transactional
    public SpendingLimitResponse setSpendingLimits(SetSpendingLimitRequest request) {
        User currentUser = userService.getCurrentUser();
        Account account = accountRepository.findByIdWithLock(request.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", request.getAccountId()));

        // Validate ownership
        if (!account.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You do not have access to this account");
        }

        // Update limits
        if (request.getDailyLimit() != null) {
            account.setDailyLimit(request.getDailyLimit());
        }
        if (request.getMonthlyLimit() != null) {
            account.setMonthlyLimit(request.getMonthlyLimit());
        }
        if (request.getSingleTransactionLimit() != null) {
            account.setSingleTransactionLimit(request.getSingleTransactionLimit());
        }

        accountRepository.save(account);

        // Log the action
        auditService.logAccountAction("SPENDING_LIMITS_UPDATED", account.getId(),
                "Limits set: daily=" + account.getDailyLimit() + 
                ", monthly=" + account.getMonthlyLimit() + 
                ", single=" + account.getSingleTransactionLimit());

        return getSpendingLimits(account.getId());
    }

    @Transactional(readOnly = true)
    public SpendingLimitResponse getSpendingLimits(Long accountId) {
        User currentUser = userService.getCurrentUser();
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));

        // Validate ownership
        if (!isAdmin(currentUser) && !account.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You do not have access to this account");
        }

        // Calculate spent amounts for today and this month
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock);
        BigDecimal dailySpent = calculateSpent(accountId, today.atStartOfDay(), now.plusNanos(1));
        BigDecimal monthlySpent = calculateSpent(accountId, today.withDayOfMonth(1).atStartOfDay(), now.plusNanos(1));

        BigDecimal dailyRemaining = account.getDailyLimit() != null 
                ? account.getDailyLimit().subtract(dailySpent).max(BigDecimal.ZERO) 
                : null;
        BigDecimal monthlyRemaining = account.getMonthlyLimit() != null 
                ? account.getMonthlyLimit().subtract(monthlySpent).max(BigDecimal.ZERO) 
                : null;

        return SpendingLimitResponse.builder()
                .accountId(account.getId())
                .accountNumber(account.getAccountNumber())
                .dailyLimit(account.getDailyLimit())
                .monthlyLimit(account.getMonthlyLimit())
                .singleTransactionLimit(account.getSingleTransactionLimit())
                .dailySpent(dailySpent)
                .monthlySpent(monthlySpent)
                .dailyRemaining(dailyRemaining)
                .monthlyRemaining(monthlyRemaining)
                .build();
    }

    public void validateSpendingLimits(Account account, BigDecimal amount, LocalDateTime timestamp) {
        if (account.getSingleTransactionLimit() != null
                && amount.compareTo(account.getSingleTransactionLimit()) > 0) {
            throw new BadRequestException("Transaction exceeds the account's single-transaction spending limit");
        }

        LocalDate day = timestamp.toLocalDate();
        LocalDateTime exclusiveEnd = timestamp.plusNanos(1);
        if (account.getDailyLimit() != null) {
            BigDecimal spent = calculateSpent(account.getId(), day.atStartOfDay(), exclusiveEnd);
            if (spent.add(amount).compareTo(account.getDailyLimit()) > 0) {
                throw new BadRequestException("Transaction exceeds the account's daily spending limit");
            }
        }
        if (account.getMonthlyLimit() != null) {
            BigDecimal spent = calculateSpent(account.getId(), day.withDayOfMonth(1).atStartOfDay(), exclusiveEnd);
            if (spent.add(amount).compareTo(account.getMonthlyLimit()) > 0) {
                throw new BadRequestException("Transaction exceeds the account's monthly spending limit");
            }
        }
    }

    private BigDecimal calculateSpent(Long accountId, LocalDateTime start, LocalDateTime end) {
        return transactionRepository.sumOutgoingSpending(accountId, start, end,
                List.of(TransactionType.WITHDRAWAL, TransactionType.TRANSFER));
    }

    private boolean isAdmin(User user) {
        return user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
