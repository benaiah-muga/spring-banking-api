package com.banking.api.repository;

import com.banking.api.entity.Account;
import com.banking.api.entity.Transaction;
import com.banking.api.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Page<Transaction> findBySourceAccountOrderByTimestampDesc(Account sourceAccount, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.sourceAccount = :account OR t.destinationAccount = :account ORDER BY t.timestamp DESC")
    Page<Transaction> findByAccountOrderByTimestampDesc(@Param("account") Account account, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.sourceAccount.id = :accountId OR t.destinationAccount.id = :accountId ORDER BY t.timestamp DESC")
    Page<Transaction> findByAccountIdOrderByTimestampDesc(@Param("accountId") Long accountId, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE (t.sourceAccount.id = :accountId OR t.destinationAccount.id = :accountId) " +
            "AND t.timestamp >= :start AND t.timestamp < :end ORDER BY t.timestamp ASC, t.id ASC")
    List<Transaction> findAccountTransactionsBetween(@Param("accountId") Long accountId,
                                                      @Param("start") LocalDateTime start,
                                                      @Param("end") LocalDateTime end,
                                                      Pageable pageable);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE (t.sourceAccount.id = :accountId OR t.destinationAccount.id = :accountId) " +
            "AND t.timestamp >= :start AND t.timestamp < :end")
    long countAccountTransactionsBetween(@Param("accountId") Long accountId,
                                        @Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(CASE " +
            "WHEN t.type = :depositType AND t.sourceAccount.id = :accountId THEN t.amount " +
            "WHEN t.type = :withdrawalType AND t.sourceAccount.id = :accountId THEN -t.amount " +
            "WHEN t.type = :transferType AND t.destinationAccount.id = :accountId THEN t.amount " +
            "WHEN t.type = :transferType AND t.sourceAccount.id = :accountId THEN -t.amount " +
            "ELSE 0 END), 0) FROM Transaction t " +
            "WHERE (t.sourceAccount.id = :accountId OR t.destinationAccount.id = :accountId) " +
            "AND t.timestamp < :before")
    BigDecimal calculateBalanceBefore(@Param("accountId") Long accountId,
                                       @Param("before") LocalDateTime before,
                                       @Param("depositType") TransactionType depositType,
                                       @Param("withdrawalType") TransactionType withdrawalType,
                                       @Param("transferType") TransactionType transferType);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.sourceAccount.id = :accountId AND t.type IN :spendingTypes " +
            "AND t.timestamp >= :start AND t.timestamp < :end")
    BigDecimal sumOutgoingSpending(@Param("accountId") Long accountId,
                                   @Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end,
                                   @Param("spendingTypes") List<TransactionType> spendingTypes);

    Page<Transaction> findAllByOrderByTimestampDesc(Pageable pageable);

    long countByType(TransactionType type);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.type = :type")
    BigDecimal sumAmountsByType(@Param("type") TransactionType type);
}
