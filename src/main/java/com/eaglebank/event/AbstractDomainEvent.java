package com.eaglebank.event;

import com.eaglebank.util.UuidGenerator;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public abstract class AbstractDomainEvent implements DomainEvent {
    
    private final UUID eventId;
    private final LocalDateTime occurredAt;
    
    protected AbstractDomainEvent() {
        this.eventId = UuidGenerator.generateUuidV7();
        this.occurredAt = LocalDateTime.now();
    }
}