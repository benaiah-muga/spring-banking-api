package com.banking.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpendingLimitResponse {

    private Long accountId;
    private String accountNumber;
    private BigDecimal dailyLimit;
    private BigDecimal monthlyLimit;
    private BigDecimal singleTransactionLimit;
    private BigDecimal dailySpent;
    private BigDecimal monthlySpent;
    private BigDecimal dailyRemaining;
    private BigDecimal monthlyRemaining;
}
