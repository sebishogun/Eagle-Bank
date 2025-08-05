package com.eaglebank.pattern.observer;

import com.eaglebank.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

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
}