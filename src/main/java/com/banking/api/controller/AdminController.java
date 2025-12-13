package com.banking.api.controller;

import com.banking.api.dto.request.UpdateAccountStatusRequest;
import com.banking.api.dto.response.AccountResponse;
import com.banking.api.dto.response.AdminDashboardResponse;
import com.banking.api.dto.response.PagedResponse;
import com.banking.api.dto.response.TransactionResponse;
import com.banking.api.dto.response.UserResponse;
import com.banking.api.entity.Account;
import com.banking.api.exception.ResourceNotFoundException;
import com.banking.api.repository.AccountRepository;
import com.banking.api.service.AdminService;
import com.banking.api.service.TransactionService;
import com.banking.api.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Admin-only endpoints for system management")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AdminService adminService;
    private final UserService userService;
    private final TransactionService transactionService;
    private final AccountRepository accountRepository;

    @GetMapping("/dashboard")
    @Operation(summary = "Get dashboard statistics", description = "Returns system-wide statistics for the admin dashboard")
    public ResponseEntity<AdminDashboardResponse> getDashboard() {
        AdminDashboardResponse stats = adminService.getDashboardStats();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/users")
    @Operation(summary = "Get all users", description = "Returns a list of all registered users")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        List<UserResponse> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Get user by ID", description = "Returns details of a specific user")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        UserResponse user = userService.getUserById(id);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/accounts")
    @Operation(summary = "Get all accounts", description = "Returns a list of all bank accounts")
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        List<Account> accounts = accountRepository.findAll();
        List<AccountResponse> response = accounts.stream()
                .map(AccountResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/accounts/{id}/status")
    @Operation(summary = "Update account status", description = "Suspend or activate a bank account")
    public ResponseEntity<AccountResponse> updateAccountStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAccountStatusRequest request) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", id));
        
        account.setStatus(request.getStatus());
        Account updatedAccount = accountRepository.save(account);
        
        return ResponseEntity.ok(AccountResponse.fromEntity(updatedAccount));
    }

    @GetMapping("/transactions")
    @Operation(summary = "Get all transactions", description = "Returns a paginated list of all transactions")
    public ResponseEntity<PagedResponse<TransactionResponse>> getAllTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        PagedResponse<TransactionResponse> transactions = transactionService.getAllTransactions(pageable);
        return ResponseEntity.ok(transactions);
    }
}
