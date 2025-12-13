package com.banking.api.controller;

import com.banking.api.dto.request.CreateAccountRequest;
import com.banking.api.dto.response.AccountResponse;
import com.banking.api.dto.response.BalanceResponse;
import com.banking.api.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/accounts")
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        AccountResponse response = accountService.createAccount(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/accounts")
    public ResponseEntity<List<AccountResponse>> getMyAccounts() {
        List<AccountResponse> accounts = accountService.getMyAccounts();
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/accounts/{id}")
    public ResponseEntity<AccountResponse> getAccountById(@PathVariable Long id) {
        AccountResponse account = accountService.getAccountById(id);
        return ResponseEntity.ok(account);
    }

    @GetMapping("/accounts/{id}/balance")
    public ResponseEntity<BalanceResponse> getAccountBalance(@PathVariable Long id) {
        BalanceResponse balance = accountService.getAccountBalance(id);
        return ResponseEntity.ok(balance);
    }

    @GetMapping("/admin/accounts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        List<AccountResponse> accounts = accountService.getAllAccounts();
        return ResponseEntity.ok(accounts);
    }
}
