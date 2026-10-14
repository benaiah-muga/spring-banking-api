package com.banking.api.service;

import com.banking.api.dto.request.CreateAccountRequest;
import com.banking.api.dto.response.AccountResponse;
import com.banking.api.dto.response.BalanceResponse;
import com.banking.api.entity.Account;
import com.banking.api.entity.User;
import com.banking.api.enums.AccountStatus;
import com.banking.api.exception.AccessDeniedException;
import com.banking.api.exception.ResourceNotFoundException;
import com.banking.api.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.security.SecureRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AccountRepository accountRepository;
    private final UserService userService;

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        User currentUser = userService.getCurrentUser();

        Account account = Account.builder()
                .accountNumber(generateAccountNumber())
                .accountType(request.getAccountType())
                .status(AccountStatus.ACTIVE)
                .user(currentUser)
                .build();

        Account savedAccount = accountRepository.save(account);
        return AccountResponse.fromEntity(savedAccount);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getMyAccounts() {
        User currentUser = userService.getCurrentUser();
        return accountRepository.findByUser(currentUser).stream()
                .map(AccountResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountById(Long accountId) {
        User currentUser = userService.getCurrentUser();
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));

        // Check ownership (unless admin)
        if (!isAdmin(currentUser) && !account.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You do not have access to this account");
        }

        return AccountResponse.fromEntity(account);
    }

    @Transactional(readOnly = true)
    public BalanceResponse getAccountBalance(Long accountId) {
        User currentUser = userService.getCurrentUser();
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));

        // Check ownership (unless admin)
        if (!isAdmin(currentUser) && !account.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You do not have access to this account");
        }

        return BalanceResponse.of(account.getId(), account.getAccountNumber(), account.getBalance());
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(AccountResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public Account getAccountEntityById(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
    }

    public Account getAccountWithLock(Long accountId) {
        return accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
    }

    public void validateAccountOwnership(Account account, User user) {
        if (!isAdmin(user) && !account.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("You do not have access to this account");
        }
    }

    private boolean isAdmin(User user) {
        return user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private String generateAccountNumber() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"));
        String random = String.format("%04d", SECURE_RANDOM.nextInt(10000));
        String accountNumber = timestamp + random;
        
        // Ensure uniqueness
        while (accountRepository.existsByAccountNumber(accountNumber)) {
            random = String.format("%04d", SECURE_RANDOM.nextInt(10000));
            accountNumber = timestamp + random;
        }
        
        return accountNumber;
    }
}
