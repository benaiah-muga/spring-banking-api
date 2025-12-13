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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final UserService userService;

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TransactionResponse deposit(DepositRequest request) {
        User currentUser = userService.getCurrentUser();
        Account account = accountService.getAccountWithLock(request.getAccountId());

        // Validate ownership
        accountService.validateAccountOwnership(account, currentUser);

        // Validate account status
        validateAccountStatus(account);

        // Update balance
        account.setBalance(account.getBalance().add(request.getAmount()));
        accountRepository.save(account);

        // Create transaction record
        Transaction transaction = Transaction.builder()
                .amount(request.getAmount())
                .type(TransactionType.DEPOSIT)
                .sourceAccount(account)
                .description(request.getDescription() != null ? request.getDescription() : "Deposit")
                .referenceNumber(generateReferenceNumber())
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);
        return TransactionResponse.fromEntity(savedTransaction);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TransactionResponse withdraw(WithdrawRequest request) {
        User currentUser = userService.getCurrentUser();
        Account account = accountService.getAccountWithLock(request.getAccountId());

        // Validate ownership
        accountService.validateAccountOwnership(account, currentUser);

        // Validate account status
        validateAccountStatus(account);

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
                .sourceAccount(account)
                .description(request.getDescription() != null ? request.getDescription() : "Withdrawal")
                .referenceNumber(generateReferenceNumber())
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);
        return TransactionResponse.fromEntity(savedTransaction);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TransactionResponse transfer(TransferRequest request) {
        // Validate source and destination are different
        if (request.getSourceAccountId().equals(request.getDestinationAccountId())) {
            throw new BadRequestException("Source and destination accounts cannot be the same");
        }

        User currentUser = userService.getCurrentUser();

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
        accountService.validateAccountOwnership(sourceAccount, currentUser);

        // Validate both accounts are active
        validateAccountStatus(sourceAccount);
        validateAccountStatus(destinationAccount);

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
        String description = request.getDescription() != null 
                ? request.getDescription() 
                : "Transfer to " + destinationAccount.getAccountNumber();

        Transaction transaction = Transaction.builder()
                .amount(request.getAmount())
                .type(TransactionType.TRANSFER)
                .sourceAccount(sourceAccount)
                .destinationAccount(destinationAccount)
                .description(description)
                .referenceNumber(generateReferenceNumber())
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);
        return TransactionResponse.fromEntity(savedTransaction);
    }

    public PagedResponse<TransactionResponse> getTransactionHistory(Long accountId, Pageable pageable) {
        User currentUser = userService.getCurrentUser();
        Account account = accountService.getAccountEntityById(accountId);

        // Validate ownership
        accountService.validateAccountOwnership(account, currentUser);

        Page<Transaction> transactions = transactionRepository.findByAccountIdOrderByTimestampDesc(accountId, pageable);
        Page<TransactionResponse> responsePage = transactions.map(TransactionResponse::fromEntity);

        return PagedResponse.from(responsePage);
    }

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

    private String generateReferenceNumber() {
        return UUID.randomUUID().toString();
    }
}
