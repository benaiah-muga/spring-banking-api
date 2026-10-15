package com.banking.api.service;

import com.banking.api.dto.request.CreatePaymentOrderRequest;
import com.banking.api.dto.response.TransactionResponse;
import com.banking.api.entity.Account;
import com.banking.api.entity.PaymentOrder;
import com.banking.api.entity.User;
import com.banking.api.enums.PaymentOrderStatus;
import com.banking.api.exception.ConflictException;
import com.banking.api.repository.PaymentOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ErpPaymentOrderServiceTest {

    private PaymentOrderRepository paymentOrderRepository;
    private TransactionService transactionService;
    private AccountService accountService;
    private UserService userService;
    private AuditService auditService;
    private ErpPaymentOrderService service;

    private User user;
    private Account account;
    private CreatePaymentOrderRequest request;

    @BeforeEach
    void setUp() {
        paymentOrderRepository = mock(PaymentOrderRepository.class);
        transactionService = mock(TransactionService.class);
        accountService = mock(AccountService.class);
        userService = mock(UserService.class);
        auditService = mock(AuditService.class);
        service = new ErpPaymentOrderService(paymentOrderRepository, transactionService, accountService,
                userService, auditService);

        user = User.builder().id(7L).build();
        account = Account.builder().id(42L).user(user).build();
        request = CreatePaymentOrderRequest.builder()
                .sourceAccountId(42L)
                .erpPaymentReference("PAY-1001")
                .invoiceReference("INV-1001")
                .beneficiaryName("Northwind Supplies")
                .beneficiaryAccount("SIM-001-9284")
                .amount(new BigDecimal("1250.00"))
                .currency("USD")
                .build();
        when(userService.getCurrentUser()).thenReturn(user);
        when(accountService.getAccountEntityById(42L)).thenReturn(account);
        when(transactionService.withdraw(any(), anyString())).thenReturn(
                TransactionResponse.builder().referenceNumber("bank-txn-1001").build());
    }

    @Test
    void settlesPaymentAndRetryReturnsOriginalOrderWithoutSecondDebit() {
        when(paymentOrderRepository.findByUserIdAndErpSystemAndErpPaymentReference(
                7L, "ChasteBusiness OS", "PAY-1001"))
                .thenReturn(Optional.empty(), Optional.empty(), Optional.of(savedOrder()));
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(invocation -> {
            PaymentOrder order = invocation.getArgument(0);
            order.setId(81L);
            return order;
        });

        var created = service.create(request, "retry-safe-key-1001");
        var replayed = service.create(request, "retry-safe-key-1001");

        assertThat(created.getStatus()).isEqualTo(PaymentOrderStatus.SETTLED);
        assertThat(created.getTransactionReference()).isEqualTo("bank-txn-1001");
        assertThat(replayed.getId()).isEqualTo(created.getId());
        assertThat(replayed.getTransactionReference()).isEqualTo(created.getTransactionReference());
        verify(transactionService, times(1)).withdraw(any(), anyString());
    }

    @Test
    void reusedErpReferenceWithChangedPayloadConflictsBeforePosting() {
        when(paymentOrderRepository.findByUserIdAndErpSystemAndErpPaymentReference(
                7L, "ChasteBusiness OS", "PAY-1001"))
                .thenReturn(Optional.of(savedOrder()));
        request.setAmount(new BigDecimal("1500.00"));

        assertThatThrownBy(() -> service.create(request, "retry-safe-key-1001"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("different request");
        verify(transactionService, never()).withdraw(any(), anyString());
    }

    @Test
    void rejectsUnsupportedCurrencyBeforePosting() {
        request.setCurrency("UGX");

        assertThatThrownBy(() -> service.create(request, "retry-safe-key-1001"))
                .hasMessageContaining("Only USD");
        verify(transactionService, never()).withdraw(any(), anyString());
    }

    private PaymentOrder savedOrder() {
        return PaymentOrder.builder()
                .id(81L)
                .user(user)
                .sourceAccount(account)
                .erpSystem("ChasteBusiness OS")
                .erpPaymentReference("PAY-1001")
                .invoiceReference("INV-1001")
                .beneficiaryName("Northwind Supplies")
                .beneficiaryAccount("SIM-001-9284")
                .amount(new BigDecimal("1250.00"))
                .currency("USD")
                .status(PaymentOrderStatus.SETTLED)
                .transactionReference("bank-txn-1001")
                .build();
    }
}
