package com.eaglebank.pattern.observer;

import com.eaglebank.event.UserLoggedInEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityEventListener {
    
    private final ConcurrentHashMap<String, AtomicInteger> loginAttempts = new ConcurrentHashMap<>();
    
    @EventListener
    @Async
    public void handleUserLoggedIn(UserLoggedInEvent event) {
        log.info("User logged in: {} from IP: {} using: {}", 
                event.getUsername(), event.getIpAddress(), event.getUserAgent());
        
        // Reset login attempts on successful login
        loginAttempts.remove(event.getUsername());
        
        // Check for suspicious patterns
        if (isSuspiciousUserAgent(event.getUserAgent())) {
            log.warn("Suspicious user agent detected for user: {} - Agent: {}", 
                    event.getUsername(), event.getUserAgent());
        }
        
        // Could log to audit trail, check geographic anomalies, etc.
    }
    
    private boolean isSuspiciousUserAgent(String userAgent) {
        if (userAgent == null) return true;
        
        // Simple check for automated tools
        String lowerAgent = userAgent.toLowerCase();
        return lowerAgent.contains("bot") || 
               lowerAgent.contains("crawler") || 
               lowerAgent.contains("spider") ||
               lowerAgent.contains("curl") ||
               lowerAgent.contains("wget");
    }
}