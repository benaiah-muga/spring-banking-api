package com.banking.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentOrderRequest {

    @NotNull
    private Long sourceAccountId;

    @NotBlank
    @Size(max = 128)
    private String erpPaymentReference;

    @Size(max = 128)
    private String invoiceReference;

    @NotBlank
    @Size(max = 150)
    private String beneficiaryName;

    @NotBlank
    @Size(max = 128)
    private String beneficiaryAccount;

    @NotNull
    @DecimalMin(value = "0.01")
    @Digits(integer = 17, fraction = 2)
    private BigDecimal amount;

    @NotBlank
    @Pattern(regexp = "USD")
    private String currency;
}
