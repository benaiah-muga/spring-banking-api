package com.banking.api.dto.response;

import com.banking.api.entity.Transaction;
import com.banking.api.enums.TransactionType;
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
public class TransactionResponse {

    private Long id;
    private BigDecimal amount;
    private TransactionType type;
    private LocalDateTime timestamp;
    private String referenceNumber;
    private String description;
    private Long sourceAccountId;
    private String sourceAccountNumber;
    private Long destinationAccountId;
    private String destinationAccountNumber;

    public static TransactionResponse fromEntity(Transaction transaction) {
        TransactionResponseBuilder builder = TransactionResponse.builder()
                .id(transaction.getId())
                .amount(transaction.getAmount())
                .type(transaction.getType())
                .timestamp(transaction.getTimestamp())
                .referenceNumber(transaction.getReferenceNumber())
                .description(transaction.getDescription())
                .sourceAccountId(transaction.getSourceAccount().getId())
                .sourceAccountNumber(transaction.getSourceAccount().getAccountNumber());

        if (transaction.getDestinationAccount() != null) {
            builder.destinationAccountId(transaction.getDestinationAccount().getId())
                    .destinationAccountNumber(transaction.getDestinationAccount().getAccountNumber());
        }

        return builder.build();
    }
}
