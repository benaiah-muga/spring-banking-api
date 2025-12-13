package com.banking.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.math.BigDecimal;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String message) {
        super(message);
    }

    public InsufficientFundsException(BigDecimal requested, BigDecimal available) {
        super(String.format("Insufficient funds. Requested: %s, Available: %s", requested, available));
    }
}
