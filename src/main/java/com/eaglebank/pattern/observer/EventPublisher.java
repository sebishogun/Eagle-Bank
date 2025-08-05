package com.eaglebank.pattern.observer;

import com.eaglebank.entity.Account;
import com.eaglebank.event.AccountStatusChangedEvent;
import com.eaglebank.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {
    
    private final ApplicationEventPublisher applicationEventPublisher;
    
    public void publishEvent(DomainEvent event) {
        if (event == null) {
            log.warn("Attempted to publish null event");
            return;
        }
        log.debug("Publishing domain event: {} [{}]", event.getEventType(), event.getEventId());
        applicationEventPublisher.publishEvent(event);
    }
    
    public void publishAccountStatusChanged(Account account, String reason) {
        AccountStatusChangedEvent event = new AccountStatusChangedEvent(
                account.getId().toString(),
                "Account",
                Map.of(
                    "accountNumber", account.getAccountNumber(),
                    "newStatus", account.getStatus().name(),
                    "reason", reason != null ? reason : "No reason provided"
                )
        );
        publishEvent(event);
    }
}