package com.banking.api.service;

import com.banking.api.dto.response.AdminDashboardResponse;
import com.banking.api.entity.Account;
import com.banking.api.entity.Transaction;
import com.banking.api.enums.AccountStatus;
import com.banking.api.enums.TransactionType;
import com.banking.api.repository.AccountRepository;
import com.banking.api.repository.TransactionRepository;
import com.banking.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public AdminDashboardResponse getDashboardStats() {
        // User statistics
        long totalUsers = userRepository.count();

        // Account statistics
        List<Account> allAccounts = accountRepository.findAll();
        long totalAccounts = allAccounts.size();
        long activeAccounts = allAccounts.stream()
                .filter(a -> a.getStatus() == AccountStatus.ACTIVE)
                .count();
        long suspendedAccounts = totalAccounts - activeAccounts;

        // Calculate system balance (sum of all account balances)
        BigDecimal systemBalance = allAccounts.stream()
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Transaction statistics
        List<Transaction> allTransactions = transactionRepository.findAll();
        long totalTransactions = allTransactions.size();

        BigDecimal totalDeposits = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.DEPOSIT)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalWithdrawals = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.WITHDRAWAL)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalTransfers = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.TRANSFER)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

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
}
