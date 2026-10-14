package com.banking.api.service;

import com.banking.api.dto.request.DepositRequest;
import com.banking.api.dto.request.TransferRequest;
import com.banking.api.dto.request.WithdrawRequest;
import com.banking.api.dto.response.PagedResponse;
import com.banking.api.dto.response.TransactionResponse;
import com.banking.api.entity.Account;
import com.banking.api.entity.Transaction;
import com.banking.api.entity.User;
import com.banking.api.enums.AccountStatus;
import com.banking.api.enums.TransactionType;
import com.banking.api.exception.AccessDeniedException;
import com.banking.api.exception.BadRequestException;
import com.banking.api.exception.InsufficientFundsException;
import com.banking.api.exception.ResourceNotFoundException;
import com.banking.api.repository.AccountRepository;
import com.banking.api.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final UserService userService;
    private final StatementService statementService;
    private final AuditService auditService;
    private final IdempotencyService idempotencyService;
    private final Clock clock;

    @Transactional
    public TransactionResponse deposit(DepositRequest request, String idempotencyKey) {
        User currentUser = userService.getCurrentUser();
        String description = request.getDescription() != null ? request.getDescription() : "Deposit";
        List<String> requestParts = List.of(request.getAccountId().toString(), canonicalAmount(request.getAmount()), description);
        TransactionResponse replay = idempotencyService.findReplay(currentUser.getId(), idempotencyKey, "DEPOSIT", requestParts);
        if (replay != null) return replay;

        Account account = accountService.getAccountWithLock(request.getAccountId());

        // Validate ownership
        validateCustomerOwnership(account, currentUser);

        // Validate account status
        validateAccountStatus(account);
        LocalDateTime timestamp = LocalDateTime.now(clock);

        // Update balance
        account.setBalance(account.getBalance().add(request.getAmount()));
        accountRepository.save(account);

        // Create transaction record
        Transaction transaction = Transaction.builder()
                .amount(request.getAmount())
                .type(TransactionType.DEPOSIT)
                .timestamp(timestamp)
                .sourceAccount(account)
                .description(description)
                .referenceNumber(generateReferenceNumber())
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);
        auditService.logTransactionAction("DEPOSIT_POSTED", savedTransaction.getId(),
                "Deposit of " + request.getAmount() + " posted to account " + account.getId());
        idempotencyService.record(currentUser.getId(), idempotencyKey, "DEPOSIT", requestParts, savedTransaction);
        return TransactionResponse.fromEntity(savedTransaction);
    }

    @Transactional
    public TransactionResponse withdraw(WithdrawRequest request, String idempotencyKey) {
        User currentUser = userService.getCurrentUser();
        String description = request.getDescription() != null ? request.getDescription() : "Withdrawal";
        List<String> requestParts = List.of(request.getAccountId().toString(), canonicalAmount(request.getAmount()), description);
        TransactionResponse replay = idempotencyService.findReplay(currentUser.getId(), idempotencyKey, "WITHDRAWAL", requestParts);
        if (replay != null) return replay;

        Account account = accountService.getAccountWithLock(request.getAccountId());

        // Validate ownership
        validateCustomerOwnership(account, currentUser);

        // Validate account status
        validateAccountStatus(account);
        LocalDateTime timestamp = LocalDateTime.now(clock);
        statementService.validateSpendingLimits(account, request.getAmount(), timestamp);

        // Check sufficient funds
        if (account.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException(request.getAmount(), account.getBalance());
        }

        // Update balance
        account.setBalance(account.getBalance().subtract(request.getAmount()));
        accountRepository.save(account);

        // Create transaction record
        Transaction transaction = Transaction.builder()
                .amount(request.getAmount())
                .type(TransactionType.WITHDRAWAL)
                .timestamp(timestamp)
                .sourceAccount(account)
                .description(description)
                .referenceNumber(generateReferenceNumber())
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);
        auditService.logTransactionAction("WITHDRAWAL_POSTED", savedTransaction.getId(),
                "Withdrawal of " + request.getAmount() + " posted from account " + account.getId());
        idempotencyService.record(currentUser.getId(), idempotencyKey, "WITHDRAWAL", requestParts, savedTransaction);
        return TransactionResponse.fromEntity(savedTransaction);
    }

    @Transactional
    public TransactionResponse transfer(TransferRequest request, String idempotencyKey) {
        // Validate source and destination are different
        if (request.getSourceAccountId().equals(request.getDestinationAccountId())) {
            throw new BadRequestException("Source and destination accounts cannot be the same");
        }

        User currentUser = userService.getCurrentUser();
        String description = request.getDescription() != null ? request.getDescription()
                : "Transfer to account " + request.getDestinationAccountId();
        List<String> requestParts = List.of(request.getSourceAccountId().toString(),
                request.getDestinationAccountId().toString(), canonicalAmount(request.getAmount()), description);
        TransactionResponse replay = idempotencyService.findReplay(currentUser.getId(), idempotencyKey, "TRANSFER", requestParts);
        if (replay != null) return replay;

        // Lock accounts in consistent order to prevent deadlocks
        Account sourceAccount;
        Account destinationAccount;
        
        if (request.getSourceAccountId() < request.getDestinationAccountId()) {
            sourceAccount = accountService.getAccountWithLock(request.getSourceAccountId());
            destinationAccount = accountService.getAccountWithLock(request.getDestinationAccountId());
        } else {
            destinationAccount = accountService.getAccountWithLock(request.getDestinationAccountId());
            sourceAccount = accountService.getAccountWithLock(request.getSourceAccountId());
        }

        // Validate ownership of source account only
        validateCustomerOwnership(sourceAccount, currentUser);

        // Validate both accounts are active
        validateAccountStatus(sourceAccount);
        validateAccountStatus(destinationAccount);
        LocalDateTime timestamp = LocalDateTime.now(clock);
        statementService.validateSpendingLimits(sourceAccount, request.getAmount(), timestamp);

        // Check sufficient funds
        if (sourceAccount.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException(request.getAmount(), sourceAccount.getBalance());
        }

        // Perform transfer
        sourceAccount.setBalance(sourceAccount.getBalance().subtract(request.getAmount()));
        destinationAccount.setBalance(destinationAccount.getBalance().add(request.getAmount()));

        accountRepository.save(sourceAccount);
        accountRepository.save(destinationAccount);

        // Create transaction record
        Transaction transaction = Transaction.builder()
                .amount(request.getAmount())
                .type(TransactionType.TRANSFER)
                .timestamp(timestamp)
                .sourceAccount(sourceAccount)
                .destinationAccount(destinationAccount)
                .description(description)
                .referenceNumber(generateReferenceNumber())
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);
        auditService.logTransactionAction("TRANSFER_POSTED", savedTransaction.getId(),
                "Transfer of " + request.getAmount() + " from account " + sourceAccount.getId()
                        + " to account " + destinationAccount.getId());
        idempotencyService.record(currentUser.getId(), idempotencyKey, "TRANSFER", requestParts, savedTransaction);
        return TransactionResponse.fromEntity(savedTransaction);
    }

    @Transactional(readOnly = true)
    public PagedResponse<TransactionResponse> getTransactionHistory(Long accountId, Pageable pageable) {
        User currentUser = userService.getCurrentUser();
        Account account = accountService.getAccountEntityById(accountId);

        // Validate ownership
        accountService.validateAccountOwnership(account, currentUser);

        Page<Transaction> transactions = transactionRepository.findByAccountIdOrderByTimestampDesc(accountId, pageable);
        Page<TransactionResponse> responsePage = transactions.map(TransactionResponse::fromEntity);

        return PagedResponse.from(responsePage);
    }

    @Transactional(readOnly = true)
    public PagedResponse<TransactionResponse> getAllTransactions(Pageable pageable) {
        Page<Transaction> transactions = transactionRepository.findAllByOrderByTimestampDesc(pageable);
        Page<TransactionResponse> responsePage = transactions.map(TransactionResponse::fromEntity);
        return PagedResponse.from(responsePage);
    }

    private void validateAccountStatus(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BadRequestException("Account is not active. Current status: " + account.getStatus());
        }
    }

    private void validateCustomerOwnership(Account account, User user) {
        if (!account.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("You must own the source account to move money");
        }
    }

    private String canonicalAmount(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }

    private String generateReferenceNumber() {
        return UUID.randomUUID().toString();
    }
}
