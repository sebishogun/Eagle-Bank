package com.eaglebank.controller;

import com.eaglebank.audit.AuditEntry;
import com.eaglebank.audit.AuditRepository;
import com.eaglebank.security.UserPrincipal;
import com.eaglebank.util.UuidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditControllerTest {
    
    @Mock
    private AuditRepository auditRepository;
    
    @InjectMocks
    private AuditController auditController;
    
    private UserPrincipal adminPrincipal;
    private UserPrincipal userPrincipal;
    private UUID userId;
    private List<AuditEntry> testAuditEntries;
    
    @BeforeEach
    void setUp() {
        userId = UuidGenerator.generateUuidV7();
        
        adminPrincipal = UserPrincipal.builder()
                .id(UuidGenerator.generateUuidV7())
                .email("admin@example.com")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .build();
                
        userPrincipal = UserPrincipal.builder()
                .id(userId)
                .email("user@example.com")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
                
        testAuditEntries = Arrays.asList(
            createAuditEntry(AuditEntry.AuditAction.CREATE, "User", userId.toString()),
            createAuditEntry(AuditEntry.AuditAction.UPDATE, "Account", UuidGenerator.generateUuidV7().toString()),
            createAuditEntry(AuditEntry.AuditAction.LOGIN, "Auth", userId.toString())
        );
    }
    
    private AuditEntry createAuditEntry(AuditEntry.AuditAction action, String entityType, String entityId) {
        AuditEntry entry = new AuditEntry();
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setUserId(userId);
        entry.setUsername("user@example.com");
        entry.setCreatedAt(LocalDateTime.now());
        return entry;
    }
    
    @Test
    @DisplayName("Admin should be able to get all audit entries")
    void getAllAuditEntries_AsAdmin_Success() {
        Pageable pageable = PageRequest.of(0, 50);
        Page<AuditEntry> page = new PageImpl<>(testAuditEntries, pageable, testAuditEntries.size());
        
        when(auditRepository.findAll(pageable)).thenReturn(page);
        
        ResponseEntity<Page<AuditEntry>> response = auditController.getAllAuditEntries(pageable);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().getContent().size());
        verify(auditRepository).findAll(pageable);
    }
    
    @Test
    @DisplayName("User should be able to get their own audit entries")
    void getUserAuditEntries_OwnEntries_Success() {
        Pageable pageable = PageRequest.of(0, 50);
        Page<AuditEntry> page = new PageImpl<>(testAuditEntries, pageable, testAuditEntries.size());
        
        when(auditRepository.findByUserId(userId, pageable)).thenReturn(page);
        
        ResponseEntity<Page<AuditEntry>> response = auditController.getUserAuditEntries(
            userPrincipal, userId, pageable);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(auditRepository).findByUserId(userId, pageable);
    }
    
    @Test
    @DisplayName("Admin should be able to get any user's audit entries")
    void getUserAuditEntries_AsAdmin_Success() {
        UUID otherUserId = UuidGenerator.generateUuidV7();
        Pageable pageable = PageRequest.of(0, 50);
        Page<AuditEntry> page = new PageImpl<>(testAuditEntries, pageable, testAuditEntries.size());
        
        when(auditRepository.findByUserId(otherUserId, pageable)).thenReturn(page);
        
        ResponseEntity<Page<AuditEntry>> response = auditController.getUserAuditEntries(
            adminPrincipal, otherUserId, pageable);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(auditRepository).findByUserId(otherUserId, pageable);
    }
    
    @Test
    @DisplayName("User should not be able to get other user's audit entries")
    void getUserAuditEntries_OtherUser_Forbidden() {
        UUID otherUserId = UuidGenerator.generateUuidV7();
        Pageable pageable = PageRequest.of(0, 50);
        
        ResponseEntity<Page<AuditEntry>> response = auditController.getUserAuditEntries(
            userPrincipal, otherUserId, pageable);
        
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNull(response.getBody());
        verify(auditRepository, never()).findByUserId(any(), any());
    }
    
    @Test
    @DisplayName("Admin should be able to get account audit entries")
    void getAccountAuditEntries_AsAdmin_Success() {
        UUID accountId = UuidGenerator.generateUuidV7();
        Pageable pageable = PageRequest.of(0, 50);
        Page<AuditEntry> page = new PageImpl<>(testAuditEntries, pageable, testAuditEntries.size());
        
        when(auditRepository.findByEntityTypeAndEntityId("Account", accountId.toString(), pageable))
            .thenReturn(page);
        
        ResponseEntity<Page<AuditEntry>> response = auditController.getAccountAuditEntries(
            accountId, pageable);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(auditRepository).findByEntityTypeAndEntityId("Account", accountId.toString(), pageable);
    }
    
    @Test
    @DisplayName("Admin should be able to get audit entries by action")
    void getAuditEntriesByAction_AsAdmin_Success() {
        AuditEntry.AuditAction action = AuditEntry.AuditAction.CREATE;
        Pageable pageable = PageRequest.of(0, 50);
        Page<AuditEntry> page = new PageImpl<>(testAuditEntries, pageable, testAuditEntries.size());
        
        when(auditRepository.findByAction(action, pageable)).thenReturn(page);
        
        ResponseEntity<Page<AuditEntry>> response = auditController.getAuditEntriesByAction(
            action, pageable);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(auditRepository).findByAction(action, pageable);
    }
    
    @Test
    @DisplayName("Admin should be able to get audit entries by date range")
    void getAuditEntriesByDateRange_AsAdmin_Success() {
        LocalDateTime start = LocalDateTime.now().minusDays(7);
        LocalDateTime end = LocalDateTime.now();
        
        when(auditRepository.findByCreatedAtBetween(start, end)).thenReturn(testAuditEntries);
        
        ResponseEntity<List<AuditEntry>> response = auditController.getAuditEntriesByDateRange(start, end);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().size());
        verify(auditRepository).findByCreatedAtBetween(start, end);
    }
    
    @Test
    @DisplayName("Admin should be able to get audit statistics")
    void getAuditStatistics_AsAdmin_Success() {
        LocalDateTime start = LocalDateTime.now().minusDays(30);
        LocalDateTime end = LocalDateTime.now();
        
        when(auditRepository.count()).thenReturn(1000L);
        when(auditRepository.countByActionAndCreatedAtBetween(AuditEntry.AuditAction.LOGIN, start, end))
            .thenReturn(100L);
        when(auditRepository.countByActionAndCreatedAtBetween(AuditEntry.AuditAction.CREATE, start, end))
            .thenReturn(50L);
        when(auditRepository.countByActionAndCreatedAtBetween(AuditEntry.AuditAction.UPDATE, start, end))
            .thenReturn(75L);
        when(auditRepository.countByActionAndCreatedAtBetween(AuditEntry.AuditAction.DELETE, start, end))
            .thenReturn(25L);
        when(auditRepository.countByActionAndCreatedAtBetween(AuditEntry.AuditAction.ACCESS_DENIED, start, end))
            .thenReturn(10L);
        
        ResponseEntity<AuditController.AuditStats> response = auditController.getAuditStatistics(start, end);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1000L, response.getBody().getTotalEntries());
        assertEquals(100L, response.getBody().getLoginCount());
        assertEquals(50L, response.getBody().getCreateCount());
        assertEquals(75L, response.getBody().getUpdateCount());
        assertEquals(25L, response.getBody().getDeleteCount());
        assertEquals(10L, response.getBody().getAccessDeniedCount());
    }
    
    @Test
    @DisplayName("Audit statistics should use default date range when not provided")
    void getAuditStatistics_NoDateRange_UsesDefaults() {
        when(auditRepository.count()).thenReturn(500L);
        when(auditRepository.countByActionAndCreatedAtBetween(any(), any(), any())).thenReturn(0L);
        
        ResponseEntity<AuditController.AuditStats> response = auditController.getAuditStatistics(null, null);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500L, response.getBody().getTotalEntries());
        
        // Verify that default date range was used (last 30 days)
        verify(auditRepository, times(5)).countByActionAndCreatedAtBetween(any(), any(), any());
    }
}