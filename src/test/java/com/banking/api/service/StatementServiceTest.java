package com.banking.api.service;

import com.banking.api.entity.Account;
import com.banking.api.exception.BadRequestException;
import com.banking.api.repository.AccountRepository;
import com.banking.api.repository.TransactionRepository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatementServiceTest {

    @Mock AccountRepository accountRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock UserService userService;
    @Mock AuditService auditService;

    private StatementService service;

    @BeforeEach
    void setUp() {
        service = new StatementService(accountRepository, transactionRepository, userService, auditService,
                Clock.fixed(Instant.parse("2026-06-15T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void generatesStatementsFromARepeatableReadSnapshot() throws NoSuchMethodException {
        Transactional transaction = StatementService.class
                .getMethod("generateStatement", Long.class, LocalDate.class, LocalDate.class)
                .getAnnotation(Transactional.class);

        org.assertj.core.api.Assertions.assertThat(transaction).isNotNull();
        org.assertj.core.api.Assertions.assertThat(transaction.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }

    @Test
    void acceptsSpendingExactlyAtDailyLimitAndRejectsAmountsOverIt() {
        Account account = Account.builder()
                .id(7L)
                .dailyLimit(new BigDecimal("50.00"))
                .build();
        LocalDateTime now = LocalDateTime.parse("2026-06-15T10:00:00");
        when(transactionRepository.sumOutgoingSpending(eq(7L), any(), any(), anyList()))
                .thenReturn(new BigDecimal("30.00"));

        assertThatCode(() -> service.validateSpendingLimits(account, new BigDecimal("20.00"), now))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> service.validateSpendingLimits(account, new BigDecimal("20.01"), now))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("daily spending limit");
    }

    @Test
    void rejectsSingleTransactionLimitBeforeQueryingHistory() {
        Account account = Account.builder()
                .id(8L)
                .singleTransactionLimit(new BigDecimal("100.00"))
                .build();

        assertThatThrownBy(() -> service.validateSpendingLimits(account,
                new BigDecimal("100.01"), LocalDateTime.parse("2026-06-15T10:00:00")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("single-transaction");
    }
}
