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
public class BalanceResponse {

    private Long accountId;
    private String accountNumber;
    private BigDecimal balance;
    private String currency;

    public static BalanceResponse of(Long accountId, String accountNumber, BigDecimal balance) {
        return BalanceResponse.builder()
                .accountId(accountId)
                .accountNumber(accountNumber)
                .balance(balance)
                .currency("USD")
                .build();
    }
}
