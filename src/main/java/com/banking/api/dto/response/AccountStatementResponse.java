package com.banking.api.dto.response;

import com.banking.api.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountStatementResponse {

    private Long accountId;
    private String accountNumber;
    private String accountHolderName;
    private String accountType;
    private LocalDate statementPeriodStart;
    private LocalDate statementPeriodEnd;
    private LocalDateTime generatedAt;
    
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private BigDecimal totalDeposits;
    private BigDecimal totalWithdrawals;
    private BigDecimal totalTransfersIn;
    private BigDecimal totalTransfersOut;
    
    private int transactionCount;
    private List<StatementTransaction> transactions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatementTransaction {
        private LocalDateTime date;
        private String referenceNumber;
        private TransactionType type;
        private String description;
        private BigDecimal amount;
        private BigDecimal balanceAfter;
    }
}
