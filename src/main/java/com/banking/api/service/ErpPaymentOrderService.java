package com.banking.api.service;

import com.banking.api.dto.request.CreatePaymentOrderRequest;
import com.banking.api.dto.request.WithdrawRequest;
import com.banking.api.dto.response.PaymentOrderResponse;
import com.banking.api.dto.response.TransactionResponse;
import com.banking.api.entity.Account;
import com.banking.api.entity.PaymentOrder;
import com.banking.api.entity.User;
import com.banking.api.enums.PaymentOrderStatus;
import com.banking.api.exception.BadRequestException;
import com.banking.api.exception.ConflictException;
import com.banking.api.exception.ResourceNotFoundException;
import com.banking.api.repository.PaymentOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ErpPaymentOrderService {

    private static final String ERP_SYSTEM = "ChasteBusiness OS";
    private static final String SUPPORTED_CURRENCY = "USD";

    private final PaymentOrderRepository paymentOrderRepository;
    private final TransactionService transactionService;
    private final AccountService accountService;
    private final UserService userService;
    private final AuditService auditService;

    @Transactional
    public PaymentOrderResponse create(CreatePaymentOrderRequest request, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);
        if (!SUPPORTED_CURRENCY.equals(request.getCurrency())) {
            throw new BadRequestException("Only " + SUPPORTED_CURRENCY + " payments are currently supported");
        }

        User user = userService.getCurrentUser();
        String erpSystem = ERP_SYSTEM;
        String erpPaymentReference = request.getErpPaymentReference().trim();
        PaymentOrder existing = paymentOrderRepository
                .findByUserIdAndErpSystemAndErpPaymentReference(user.getId(), erpSystem, erpPaymentReference)
                .orElse(null);
        if (existing != null) {
            assertSameRequest(existing, request, erpSystem, erpPaymentReference);
            return PaymentOrderResponse.fromEntity(existing);
        }

        String description = "ERP payment " + erpSystem + " / " + erpPaymentReference;
        TransactionResponse transaction = transactionService.withdraw(
                WithdrawRequest.builder()
                        .accountId(request.getSourceAccountId())
                        .amount(request.getAmount())
                        .description(description)
                        .build(),
                internalIdempotencyKey(user.getId(), idempotencyKey));

        // A concurrent retry with the same Idempotency-Key can finish while this request
        // waits on the transaction idempotency lock. Re-read before inserting the order.
        PaymentOrder concurrentReplay = paymentOrderRepository
                .findByUserIdAndErpSystemAndErpPaymentReference(user.getId(), erpSystem, erpPaymentReference)
                .orElse(null);
        if (concurrentReplay != null) {
            assertSameRequest(concurrentReplay, request, erpSystem, erpPaymentReference);
            return PaymentOrderResponse.fromEntity(concurrentReplay);
        }

        Account sourceAccount = accountService.getAccountEntityById(request.getSourceAccountId());
        PaymentOrder paymentOrder = PaymentOrder.builder()
                .user(user)
                .sourceAccount(sourceAccount)
                .erpSystem(erpSystem)
                .erpPaymentReference(erpPaymentReference)
                .invoiceReference(blankToNull(request.getInvoiceReference()))
                .beneficiaryName(request.getBeneficiaryName().trim())
                .beneficiaryAccount(request.getBeneficiaryAccount().trim())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status(PaymentOrderStatus.SETTLED)
                .transactionReference(transaction.getReferenceNumber())
                .build();
        PaymentOrder saved = paymentOrderRepository.save(paymentOrder);
        auditService.log("ERP_PAYMENT_SETTLED", "PAYMENT_ORDER", saved.getId(),
                "ERP payment " + erpSystem + " / " + erpPaymentReference + " settled as bank transaction "
                        + transaction.getReferenceNumber());
        return PaymentOrderResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public PaymentOrderResponse get(Long paymentOrderId) {
        User user = userService.getCurrentUser();
        PaymentOrder paymentOrder = paymentOrderRepository.findByIdAndUserId(paymentOrderId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment order", "id", paymentOrderId));
        return PaymentOrderResponse.fromEntity(paymentOrder);
    }

    private void assertSameRequest(PaymentOrder existing, CreatePaymentOrderRequest request,
                                   String erpSystem, String erpPaymentReference) {
        boolean matches = existing.getSourceAccount().getId().equals(request.getSourceAccountId())
                && existing.getErpSystem().equals(erpSystem)
                && existing.getErpPaymentReference().equals(erpPaymentReference)
                && Objects.equals(existing.getInvoiceReference(), blankToNull(request.getInvoiceReference()))
                && existing.getBeneficiaryName().equals(request.getBeneficiaryName().trim())
                && existing.getBeneficiaryAccount().equals(request.getBeneficiaryAccount().trim())
                && existing.getAmount().compareTo(request.getAmount()) == 0
                && existing.getCurrency().equals(request.getCurrency());
        if (!matches) {
            throw new ConflictException("This ERP payment reference was already used with a different request");
        }
    }

    private String internalIdempotencyKey(Long userId, String idempotencyKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] value = ("erp-payment:" + userId + ":" + idempotencyKey).getBytes(StandardCharsets.UTF_8);
            return "erp-" + HexFormat.of().formatHex(digest.digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new BadRequestException("Idempotency-Key must contain between 1 and 128 characters");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
