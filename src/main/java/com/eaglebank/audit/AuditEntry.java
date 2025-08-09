package com.eaglebank.audit;

import com.eaglebank.util.UuidGenerator;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Entity
@Table(name = "audit_log")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEntry {
    
    @Id
    @Builder.Default
    private UUID id = UuidGenerator.generateUuidV7();
    
    @Column(name = "user_id")
    private UUID userId;
    
    @Column(name = "username")
    private String username;
    
    @Column(name = "action", nullable = false)
    @Enumerated(EnumType.STRING)
    private AuditAction action;
    
    @Column(name = "entity_type", nullable = false)
    private String entityType;
    
    @Column(name = "entity_id")
    private String entityId;
    
    @Column(name = "ip_address")
    private String ipAddress;
    
    @Column(name = "user_agent")
    private String userAgent;
    
    @Column(name = "request_method")
    private String requestMethod;
    
    @Column(name = "request_path")
    private String requestPath;
    
    @Column(name = "status_code")
    private Integer statusCode;
    
    @Column(name = "error_message")
    private String errorMessage;
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "audit_log_details", joinColumns = @JoinColumn(name = "audit_id"))
    @MapKeyColumn(name = "detail_key")
    @Column(name = "detail_value")
    private Map<String, String> details;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    public enum AuditAction {
        LOGIN,
        LOGOUT,
        TOKEN_REFRESH,
        CREATE,
        READ,
        UPDATE,
        DELETE,
        DEPOSIT,
        WITHDRAWAL,
        TRANSFER,
        PASSWORD_CHANGE,
        FAILED_LOGIN,
        ACCESS_DENIED
    }
}