package com.eaglebank.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {
    
    private final AuditRepository auditRepository;
    
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void audit(AuditEntry entry) {
        try {
            auditRepository.save(entry);
            log.debug("Audit entry saved: {} - {} on {}", 
                    entry.getUsername(), entry.getAction(), entry.getEntityType());
        } catch (Exception e) {
            log.error("Failed to save audit entry: {}", e.getMessage());
        }
    }
    
    public void auditLogin(UUID userId, String username, String ipAddress, String userAgent) {
        AuditEntry entry = AuditEntry.builder()
                .userId(userId)
                .username(username)
                .action(AuditEntry.AuditAction.LOGIN)
                .entityType("User")
                .entityId(userId.toString())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();
        
        audit(entry);
    }
    
    public void auditTransaction(UUID userId, String username, UUID transactionId, 
                                AuditEntry.AuditAction action, Map<String, String> details) {
        AuditEntry entry = AuditEntry.builder()
                .userId(userId)
                .username(username)
                .action(action)
                .entityType("Transaction")
                .entityId(transactionId.toString())
                .details(details)
                .build();
        
        audit(entry);
    }
    
    public void auditAccountAccess(UUID userId, String username, UUID accountId, 
                                  AuditEntry.AuditAction action, String ipAddress) {
        AuditEntry entry = AuditEntry.builder()
                .userId(userId)
                .username(username)
                .action(action)
                .entityType("Account")
                .entityId(accountId.toString())
                .ipAddress(ipAddress)
                .build();
        
        audit(entry);
    }
    
    public void auditFailedAccess(String username, String reason, String ipAddress, String requestPath) {
        AuditEntry entry = AuditEntry.builder()
                .username(username)
                .action(AuditEntry.AuditAction.ACCESS_DENIED)
                .entityType("System")
                .ipAddress(ipAddress)
                .requestPath(requestPath)
                .errorMessage(reason)
                .build();
        
        audit(entry);
    }
    
    public void auditTokenRefresh(UUID userId, String userEmail, String ipAddress) {
        AuditEntry entry = AuditEntry.builder()
                .action(AuditEntry.AuditAction.TOKEN_REFRESH)
                .entityId(userId != null ? userId.toString() : null)
                .entityType("User")
                .username(userEmail)
                .userId(userId)
                .ipAddress(ipAddress)
                .build();
        
        audit(entry);
    }
}