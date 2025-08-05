package com.eaglebank.audit;

import com.eaglebank.util.UuidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {
    
    @Mock
    private AuditRepository auditRepository;
    
    @InjectMocks
    private AuditService auditService;
    
    private UUID userId;
    private String username;
    
    @BeforeEach
    void setUp() {
        userId = UuidGenerator.generateUuidV7();
        username = "test@example.com";
    }
    
    @Test
    @DisplayName("Should audit entry asynchronously")
    void shouldAuditEntryAsync() {
        AuditEntry entry = AuditEntry.builder()
                .userId(userId)
                .username(username)
                .action(AuditEntry.AuditAction.LOGIN)
                .entityType("User")
                .entityId(userId.toString())
                .build();
        
        auditService.audit(entry);
        
        // Due to async nature, may need to wait or use different verification
        verify(auditRepository, timeout(1000)).save(entry);
    }
    
    @Test
    @DisplayName("Should handle audit failures gracefully")
    void shouldHandleAuditFailuresGracefully() {
        AuditEntry entry = AuditEntry.builder()
                .userId(userId)
                .username(username)
                .action(AuditEntry.AuditAction.CREATE)
                .entityType("Account")
                .build();
        
        // Mock repository to throw exception
        when(auditRepository.save(any(AuditEntry.class)))
                .thenThrow(new RuntimeException("Database error"));
        
        // Should not throw exception
        assertDoesNotThrow(() -> auditService.audit(entry));
    }
    
    @Test
    @DisplayName("Should audit login")
    void shouldAuditLogin() {
        String ipAddress = "192.168.1.100";
        String userAgent = "Mozilla/5.0";
        
        auditService.auditLogin(userId, username, ipAddress, userAgent);
        
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditRepository, timeout(1000)).save(captor.capture());
        
        AuditEntry saved = captor.getValue();
        assertEquals(userId, saved.getUserId());
        assertEquals(username, saved.getUsername());
        assertEquals(AuditEntry.AuditAction.LOGIN, saved.getAction());
        assertEquals("User", saved.getEntityType());
        assertEquals(userId.toString(), saved.getEntityId());
        assertEquals(ipAddress, saved.getIpAddress());
        assertEquals(userAgent, saved.getUserAgent());
    }
    
    @Test
    @DisplayName("Should audit transaction")
    void shouldAuditTransaction() {
        UUID transactionId = UuidGenerator.generateUuidV7();
        Map<String, String> details = new HashMap<>();
        details.put("amount", "1000.00");
        details.put("type", "DEPOSIT");
        
        auditService.auditTransaction(userId, username, transactionId, 
                AuditEntry.AuditAction.DEPOSIT, details);
        
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditRepository, timeout(1000)).save(captor.capture());
        
        AuditEntry saved = captor.getValue();
        assertEquals(userId, saved.getUserId());
        assertEquals(username, saved.getUsername());
        assertEquals(AuditEntry.AuditAction.DEPOSIT, saved.getAction());
        assertEquals("Transaction", saved.getEntityType());
        assertEquals(transactionId.toString(), saved.getEntityId());
        assertEquals(details, saved.getDetails());
    }
    
    @Test
    @DisplayName("Should audit account access")
    void shouldAuditAccountAccess() {
        UUID accountId = UuidGenerator.generateUuidV7();
        String ipAddress = "192.168.1.100";
        
        auditService.auditAccountAccess(userId, username, accountId, 
                AuditEntry.AuditAction.READ, ipAddress);
        
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditRepository, timeout(1000)).save(captor.capture());
        
        AuditEntry saved = captor.getValue();
        assertEquals(userId, saved.getUserId());
        assertEquals(username, saved.getUsername());
        assertEquals(AuditEntry.AuditAction.READ, saved.getAction());
        assertEquals("Account", saved.getEntityType());
        assertEquals(accountId.toString(), saved.getEntityId());
        assertEquals(ipAddress, saved.getIpAddress());
    }
    
    @Test
    @DisplayName("Should audit failed access")
    void shouldAuditFailedAccess() {
        String reason = "Invalid credentials";
        String ipAddress = "192.168.1.100";
        String requestPath = "/v1/auth/login";
        
        auditService.auditFailedAccess(username, reason, ipAddress, requestPath);
        
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditRepository, timeout(1000)).save(captor.capture());
        
        AuditEntry saved = captor.getValue();
        assertNull(saved.getUserId()); // No user ID for failed access
        assertEquals(username, saved.getUsername());
        assertEquals(AuditEntry.AuditAction.ACCESS_DENIED, saved.getAction());
        assertEquals("System", saved.getEntityType());
        assertEquals(ipAddress, saved.getIpAddress());
        assertEquals(requestPath, saved.getRequestPath());
        assertEquals(reason, saved.getErrorMessage());
    }
}