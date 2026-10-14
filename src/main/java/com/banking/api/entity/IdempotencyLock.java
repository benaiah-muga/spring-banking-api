package com.banking.api.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "idempotency_locks")
@Getter
@NoArgsConstructor
public class IdempotencyLock {

    @Id
    private Integer id;

    public IdempotencyLock(Integer id) {
        this.id = id;
    }
}
