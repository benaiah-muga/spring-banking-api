package com.banking.api.dto.request;

import com.banking.api.enums.AccountStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAccountStatusRequest {

    @NotNull(message = "Account status is required")
    private AccountStatus status;

    private String reason;
}
