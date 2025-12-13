package com.banking.api.service;

import com.banking.api.entity.AuditLog;
import com.banking.api.entity.User;
import com.banking.api.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void log(String action, String entityType, Long entityId, String details) {
        AuditLog.AuditLogBuilder builder = AuditLog.builder()
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .details(details);

        // Get current user if authenticated
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            builder.userId(user.getId())
                    .userEmail(user.getEmail());
        }

        auditLogRepository.save(builder.build());
    }

    public void logUserAction(String action, Long userId, String details) {
        log(action, "USER", userId, details);
    }

    public void logAccountAction(String action, Long accountId, String details) {
        log(action, "ACCOUNT", accountId, details);
    }

    public void logTransactionAction(String action, Long transactionId, String details) {
        log(action, "TRANSACTION", transactionId, details);
    }

    public Page<AuditLog> getAuditLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByTimestampDesc(pageable);
    }

    public Page<AuditLog> getAuditLogsByUser(Long userId, Pageable pageable) {
        return auditLogRepository.findByUserIdOrderByTimestampDesc(userId, pageable);
    }

    public Page<AuditLog> getAuditLogsByEntityType(String entityType, Pageable pageable) {
        return auditLogRepository.findByEntityTypeOrderByTimestampDesc(entityType, pageable);
    }
}
