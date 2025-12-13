package com.banking.api.dto.response;

import com.banking.api.entity.Account;
import com.banking.api.enums.AccountStatus;
import com.banking.api.enums.AccountType;
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
public class AccountResponse {

    private Long id;
    private String accountNumber;
    private BigDecimal balance;
    private AccountType accountType;
    private AccountStatus status;
    private Long userId;
    private String userFullName;
    private LocalDateTime createdAt;

    public static AccountResponse fromEntity(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .balance(account.getBalance())
                .accountType(account.getAccountType())
                .status(account.getStatus())
                .userId(account.getUser().getId())
                .userFullName(account.getUser().getFullName())
                .createdAt(account.getCreatedAt())
                .build();
    }
}
