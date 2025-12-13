package com.banking.api.service;

import com.banking.api.dto.request.SetSpendingLimitRequest;
import com.banking.api.dto.response.AccountStatementResponse;
import com.banking.api.dto.response.SpendingLimitResponse;
import com.banking.api.entity.Account;
import com.banking.api.entity.Transaction;
import com.banking.api.entity.User;
import com.banking.api.enums.TransactionType;
import com.banking.api.exception.AccessDeniedException;
import com.banking.api.exception.ResourceNotFoundException;
import com.banking.api.repository.AccountRepository;
import com.banking.api.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatementService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UserService userService;
    private final AuditService auditService;

    public AccountStatementResponse generateStatement(Long accountId, LocalDate startDate, LocalDate endDate) {
        User currentUser = userService.getCurrentUser();
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));

        // Validate ownership (unless admin)
        if (!isAdmin(currentUser) && !account.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You do not have access to this account");
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        // Get all transactions for the account in the date range
        Pageable pageable = PageRequest.of(0, 1000); // Get up to 1000 transactions
        Page<Transaction> transactionPage = transactionRepository.findByAccountIdOrderByTimestampDesc(accountId, pageable);
        
        List<Transaction> periodTransactions = transactionPage.getContent().stream()
                .filter(t -> !t.getTimestamp().isBefore(startDateTime) && !t.getTimestamp().isAfter(endDateTime))
                .sorted(Comparator.comparing(Transaction::getTimestamp))
                .toList();

        // Calculate totals
        BigDecimal totalDeposits = BigDecimal.ZERO;
        BigDecimal totalWithdrawals = BigDecimal.ZERO;
        BigDecimal totalTransfersIn = BigDecimal.ZERO;
        BigDecimal totalTransfersOut = BigDecimal.ZERO;

        List<AccountStatementResponse.StatementTransaction> statementTransactions = new ArrayList<>();
        BigDecimal runningBalance = account.getBalance(); // We'll calculate backwards

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

        // Calculate opening balance (current balance - net change)
        BigDecimal netChange = totalDeposits.add(totalTransfersIn)
                .subtract(totalWithdrawals).subtract(totalTransfersOut);
        BigDecimal openingBalance = account.getBalance().subtract(netChange);

        // Build statement transactions
        runningBalance = openingBalance;
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
                .generatedAt(LocalDateTime.now())
                .openingBalance(openingBalance)
                .closingBalance(account.getBalance())
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
        Account account = accountRepository.findById(request.getAccountId())
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

    public SpendingLimitResponse getSpendingLimits(Long accountId) {
        User currentUser = userService.getCurrentUser();
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));

        // Validate ownership
        if (!isAdmin(currentUser) && !account.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You do not have access to this account");
        }

        // Calculate spent amounts for today and this month
        BigDecimal dailySpent = calculateDailySpent(accountId);
        BigDecimal monthlySpent = calculateMonthlySpent(accountId);

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

    private BigDecimal calculateDailySpent(Long accountId) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        Pageable pageable = PageRequest.of(0, 1000);
        Page<Transaction> transactions = transactionRepository.findByAccountIdOrderByTimestampDesc(accountId, pageable);
        
        return transactions.getContent().stream()
                .filter(t -> t.getTimestamp().isAfter(startOfDay))
                .filter(t -> t.getType() == TransactionType.WITHDRAWAL || 
                        (t.getType() == TransactionType.TRANSFER && t.getSourceAccount().getId().equals(accountId)))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateMonthlySpent(Long accountId) {
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        Pageable pageable = PageRequest.of(0, 1000);
        Page<Transaction> transactions = transactionRepository.findByAccountIdOrderByTimestampDesc(accountId, pageable);
        
        return transactions.getContent().stream()
                .filter(t -> t.getTimestamp().isAfter(startOfMonth))
                .filter(t -> t.getType() == TransactionType.WITHDRAWAL || 
                        (t.getType() == TransactionType.TRANSFER && t.getSourceAccount().getId().equals(accountId)))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isAdmin(User user) {
        return user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
