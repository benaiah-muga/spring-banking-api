package com.banking.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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

    private Long accountId;

    @DecimalMin(value = "0.00", message = "Daily limit must be zero or positive")
    @Digits(integer = 17, fraction = 2)
    private BigDecimal dailyLimit;

    @DecimalMin(value = "0.00", message = "Monthly limit must be zero or positive")
    @Digits(integer = 17, fraction = 2)
    private BigDecimal monthlyLimit;

    @DecimalMin(value = "0.00", message = "Transaction limit must be zero or positive")
    @Digits(integer = 17, fraction = 2)
    private BigDecimal singleTransactionLimit;
}
