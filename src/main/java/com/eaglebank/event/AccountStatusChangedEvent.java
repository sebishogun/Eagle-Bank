package com.eaglebank.event;

import com.eaglebank.util.UuidGenerator;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
public class AccountStatusChangedEvent implements DomainEvent {
    private final UUID eventId;
    private final LocalDateTime occurredAt;
    private final String eventType;
    private final String entityId;
    private final String entityType;
    private final Map<String, Object> eventData;
    
    public AccountStatusChangedEvent(String entityId, String entityType, Map<String, Object> eventData) {
        this.eventId = UuidGenerator.generateUuidV7();
        this.occurredAt = LocalDateTime.now();
        this.eventType = "ACCOUNT_STATUS_CHANGED";
        this.entityId = entityId;
        this.entityType = entityType;
        this.eventData = eventData;
    }
}