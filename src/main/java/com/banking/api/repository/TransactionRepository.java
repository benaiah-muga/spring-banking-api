package com.banking.api.repository;

import com.banking.api.entity.Account;
import com.banking.api.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Page<Transaction> findBySourceAccountOrderByTimestampDesc(Account sourceAccount, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.sourceAccount = :account OR t.destinationAccount = :account ORDER BY t.timestamp DESC")
    Page<Transaction> findByAccountOrderByTimestampDesc(@Param("account") Account account, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.sourceAccount.id = :accountId OR t.destinationAccount.id = :accountId ORDER BY t.timestamp DESC")
    Page<Transaction> findByAccountIdOrderByTimestampDesc(@Param("accountId") Long accountId, Pageable pageable);

    Page<Transaction> findAllByOrderByTimestampDesc(Pageable pageable);
}
