package com.banking.api.repository;

import com.banking.api.entity.IdempotencyLock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IdempotencyLockRepository extends JpaRepository<IdempotencyLock, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM IdempotencyLock l WHERE l.id = :id")
    Optional<IdempotencyLock> findByIdForUpdate(@Param("id") Integer id);
}
