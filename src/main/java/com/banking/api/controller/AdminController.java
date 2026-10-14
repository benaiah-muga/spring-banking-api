package com.banking.api.controller;

import com.banking.api.dto.request.UpdateAccountStatusRequest;
import com.banking.api.dto.response.*;
import com.banking.api.entity.AuditLog;
import com.banking.api.enums.Role;
import com.banking.api.repository.UserRepository;
import com.banking.api.service.AdminService;
import com.banking.api.service.AuditService;
import com.banking.api.service.TransactionService;
import com.banking.api.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.banking.api.util.PaginationSupport;

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
    private final UserRepository userRepository;
    private final AuditService auditService;

    @GetMapping("/dashboard")
    @Operation(summary = "Get dashboard statistics", description = "Returns system-wide statistics for the admin dashboard")
    public ResponseEntity<AdminDashboardResponse> getDashboard() {
        AdminDashboardResponse stats = adminService.getDashboardStats();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/users")
    @Operation(summary = "Get all users or search", description = "Returns users with optional search filters")
    public ResponseEntity<PagedResponse<UserResponse>> getAllUsers(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String fullName,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        
        Sort sort = sortDir.equalsIgnoreCase("desc") 
                ? Sort.by(sortBy).descending() 
                : Sort.by(sortBy).ascending();
        Pageable pageable = PaginationSupport.page(page, size, sort);

        Role roleEnum = null;
        if (role != null && !role.isEmpty()) {
            try {
                roleEnum = Role.valueOf(role.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        Page<UserResponse> users = userRepository.searchUsers(email, fullName, roleEnum, pageable)
                .map(UserResponse::fromEntity);
        
        return ResponseEntity.ok(PagedResponse.from(users));
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Get user by ID", description = "Returns details of a specific user")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        UserResponse user = userService.getUserById(id);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/accounts")
    @Operation(summary = "Get all accounts", description = "Returns a paginated list of bank accounts")
    public ResponseEntity<PagedResponse<AccountResponse>> getAllAccounts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PagedResponse<AccountResponse> response = adminService.getAllAccounts(page, size);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/accounts/{id}/status")
    @Operation(summary = "Update account status", description = "Suspend or activate a bank account")
    public ResponseEntity<AccountResponse> updateAccountStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAccountStatusRequest request) {
        return ResponseEntity.ok(adminService.updateAccountStatus(id, request));
    }

    @GetMapping("/transactions")
    @Operation(summary = "Get all transactions", description = "Returns a paginated list of all transactions")
    public ResponseEntity<PagedResponse<TransactionResponse>> getAllTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PaginationSupport.page(page, size);
        PagedResponse<TransactionResponse> transactions = transactionService.getAllTransactions(pageable);
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Get audit logs", description = "Returns a paginated list of audit logs")
    public ResponseEntity<PagedResponse<AuditLogResponse>> getAuditLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String entityType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PaginationSupport.page(page, size);
        Page<AuditLog> logs;

        if (userId != null) {
            logs = auditService.getAuditLogsByUser(userId, pageable);
        } else if (entityType != null) {
            logs = auditService.getAuditLogsByEntityType(entityType, pageable);
        } else {
            logs = auditService.getAuditLogs(pageable);
        }

        Page<AuditLogResponse> response = logs.map(AuditLogResponse::fromEntity);
        return ResponseEntity.ok(PagedResponse.from(response));
    }
}
