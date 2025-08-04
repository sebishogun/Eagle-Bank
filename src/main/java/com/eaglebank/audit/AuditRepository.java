package com.eaglebank.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditRepository extends JpaRepository<AuditEntry, UUID> {
    
    Page<AuditEntry> findByUserId(UUID userId, Pageable pageable);
    
    Page<AuditEntry> findByUsername(String username, Pageable pageable);
    
    Page<AuditEntry> findByAction(AuditEntry.AuditAction action, Pageable pageable);
    
    Page<AuditEntry> findByEntityTypeAndEntityId(String entityType, String entityId, Pageable pageable);
    
    List<AuditEntry> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    Page<AuditEntry> findByIpAddress(String ipAddress, Pageable pageable);
    
    long countByActionAndCreatedAtBetween(AuditEntry.AuditAction action, LocalDateTime start, LocalDateTime end);
}