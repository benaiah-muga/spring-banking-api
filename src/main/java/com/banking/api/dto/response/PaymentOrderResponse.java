package com.banking.api.dto.response;

import com.banking.api.entity.PaymentOrder;
import com.banking.api.enums.PaymentOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrderResponse {

    private Long id;
    private Long userId;
    private Long sourceAccountId;
    private String erpSystem;
    private String erpPaymentReference;
    private String invoiceReference;
    private String beneficiaryName;
    private String beneficiaryAccount;
    private BigDecimal amount;
    private String currency;
    private PaymentOrderStatus status;
    private String transactionReference;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PaymentOrderResponse fromEntity(PaymentOrder order) {
        return PaymentOrderResponse.builder()
                .id(order.getId())
                .userId(order.getUser().getId())
                .sourceAccountId(order.getSourceAccount().getId())
                .erpSystem(order.getErpSystem())
                .erpPaymentReference(order.getErpPaymentReference())
                .invoiceReference(order.getInvoiceReference())
                .beneficiaryName(order.getBeneficiaryName())
                .beneficiaryAccount(order.getBeneficiaryAccount())
                .amount(order.getAmount())
                .currency(order.getCurrency())
                .status(order.getStatus())
                .transactionReference(order.getTransactionReference())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
