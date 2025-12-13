package com.banking.api.controller;

import com.banking.api.dto.request.CreateAccountRequest;
import com.banking.api.dto.request.SetSpendingLimitRequest;
import com.banking.api.dto.response.AccountResponse;
import com.banking.api.dto.response.AccountStatementResponse;
import com.banking.api.dto.response.BalanceResponse;
import com.banking.api.dto.response.SpendingLimitResponse;
import com.banking.api.service.AccountService;
import com.banking.api.service.StatementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Account management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

    private final AccountService accountService;
    private final StatementService statementService;

    @PostMapping("/accounts")
    @Operation(summary = "Create new account", description = "Create a new bank account for the authenticated user")
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        AccountResponse response = accountService.createAccount(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/accounts")
    @Operation(summary = "Get my accounts", description = "Get all accounts belonging to the authenticated user")
    public ResponseEntity<List<AccountResponse>> getMyAccounts() {
        List<AccountResponse> accounts = accountService.getMyAccounts();
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/accounts/{id}")
    @Operation(summary = "Get account by ID", description = "Get account details by ID")
    public ResponseEntity<AccountResponse> getAccountById(@PathVariable Long id) {
        AccountResponse account = accountService.getAccountById(id);
        return ResponseEntity.ok(account);
    }

    @GetMapping("/accounts/{id}/balance")
    @Operation(summary = "Get account balance", description = "Get current balance of an account")
    public ResponseEntity<BalanceResponse> getAccountBalance(@PathVariable Long id) {
        BalanceResponse balance = accountService.getAccountBalance(id);
        return ResponseEntity.ok(balance);
    }

    @GetMapping("/accounts/{id}/statement")
    @Operation(summary = "Get account statement", description = "Generate account statement for a date range")
    public ResponseEntity<AccountStatementResponse> getAccountStatement(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        AccountStatementResponse statement = statementService.generateStatement(id, startDate, endDate);
        return ResponseEntity.ok(statement);
    }

    @GetMapping("/accounts/{id}/spending-limits")
    @Operation(summary = "Get spending limits", description = "Get spending limits and current usage for an account")
    public ResponseEntity<SpendingLimitResponse> getSpendingLimits(@PathVariable Long id) {
        SpendingLimitResponse limits = statementService.getSpendingLimits(id);
        return ResponseEntity.ok(limits);
    }

    @PutMapping("/accounts/{id}/spending-limits")
    @Operation(summary = "Set spending limits", description = "Set daily, monthly, and per-transaction spending limits")
    public ResponseEntity<SpendingLimitResponse> setSpendingLimits(
            @PathVariable Long id,
            @Valid @RequestBody SetSpendingLimitRequest request) {
        request.setAccountId(id);
        SpendingLimitResponse limits = statementService.setSpendingLimits(request);
        return ResponseEntity.ok(limits);
    }

    @GetMapping("/admin/accounts")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all accounts (Admin)", description = "Admin: Get all accounts in the system")
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        List<AccountResponse> accounts = accountService.getAllAccounts();
        return ResponseEntity.ok(accounts);
    }
}
