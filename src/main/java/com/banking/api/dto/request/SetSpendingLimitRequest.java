package com.banking.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetSpendingLimitRequest {

    @NotNull(message = "Account ID is required")
    private Long accountId;

    @DecimalMin(value = "0.00", message = "Daily limit must be zero or positive")
    private BigDecimal dailyLimit;

    @DecimalMin(value = "0.00", message = "Monthly limit must be zero or positive")
    private BigDecimal monthlyLimit;

    @DecimalMin(value = "0.00", message = "Transaction limit must be zero or positive")
    private BigDecimal singleTransactionLimit;
}
