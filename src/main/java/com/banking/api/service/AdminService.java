package com.banking.api.service;

import com.banking.api.dto.response.AdminDashboardResponse;
import com.banking.api.entity.Account;
import com.banking.api.dto.request.UpdateAccountStatusRequest;
import com.banking.api.dto.response.AccountResponse;
import com.banking.api.dto.response.PagedResponse;
import com.banking.api.exception.ResourceNotFoundException;
import com.banking.api.enums.AccountStatus;
import com.banking.api.enums.TransactionType;
import com.banking.api.repository.AccountRepository;
import com.banking.api.repository.TransactionRepository;
import com.banking.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Valid;
import com.banking.api.util.PaginationSupport;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;

    public AdminDashboardResponse getDashboardStats() {
        // User statistics
        long totalUsers = userRepository.count();

        // Account statistics
        long activeAccounts = accountRepository.countByStatus(AccountStatus.ACTIVE);
        long totalAccounts = accountRepository.count();
        long suspendedAccounts = totalAccounts - activeAccounts;
        BigDecimal systemBalance = accountRepository.sumBalances();
        long totalTransactions = transactionRepository.count();
        BigDecimal totalDeposits = transactionRepository.sumAmountsByType(TransactionType.DEPOSIT);
        BigDecimal totalWithdrawals = transactionRepository.sumAmountsByType(TransactionType.WITHDRAWAL);
        BigDecimal totalTransfers = transactionRepository.sumAmountsByType(TransactionType.TRANSFER);

        return AdminDashboardResponse.builder()
                .totalUsers(totalUsers)
                .totalAccounts(totalAccounts)
                .activeAccounts(activeAccounts)
                .suspendedAccounts(suspendedAccounts)
                .totalTransactions(totalTransactions)
                .totalDeposits(totalDeposits)
                .totalWithdrawals(totalWithdrawals)
                .totalTransfers(totalTransfers)
                .systemBalance(systemBalance)
                .build();
    }

    @Transactional(readOnly = true)
    public PagedResponse<AccountResponse> getAllAccounts(int page, int size) {
        Pageable pageable = PaginationSupport.page(page, size);
        Page<AccountResponse> accounts = accountRepository.findAll(pageable).map(AccountResponse::fromEntity);
        return PagedResponse.from(accounts);
    }

    @Transactional
    public AccountResponse updateAccountStatus(Long id, @Valid UpdateAccountStatusRequest request) {
        Account account = accountRepository.findByIdWithLock(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", id));
        account.setStatus(request.getStatus());
        Account savedAccount = accountRepository.save(account);
        auditService.logAccountAction("STATUS_CHANGED", id,
                "Status changed to " + request.getStatus()
                        + (request.getReason() != null ? ". Reason: " + request.getReason() : ""));
        return AccountResponse.fromEntity(savedAccount);
    }
}
