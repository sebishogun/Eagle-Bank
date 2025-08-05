package com.eaglebank.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service to track login attempts and implement account lockout after failed attempts
 */
@Service
@Slf4j
public class LoginAttemptService {
    
    @Value("${security.login.max-attempts:5}")
    private int maxAttempts;
    
    @Value("${security.login.lockout-duration-minutes:30}")
    private int lockoutDurationMinutes;
    
    private final Map<String, AttemptRecord> attempts = new ConcurrentHashMap<>();
    
    /**
     * Records a successful login attempt
     */
    public void loginSucceeded(String username) {
        log.debug("Login succeeded for user: {}", username);
        attempts.remove(username);
    }
    
    /**
     * Records a failed login attempt
     */
    public void loginFailed(String username) {
        log.warn("Login failed for user: {}", username);
        
        AttemptRecord record = attempts.compute(username, (key, existingRecord) -> {
            if (existingRecord == null) {
                return new AttemptRecord();
            }
            
            // Check if lockout has expired
            if (existingRecord.isLocked() && existingRecord.hasLockoutExpired(lockoutDurationMinutes)) {
                log.info("Lockout expired for user: {}, resetting attempts", username);
                return new AttemptRecord();
            }
            
            existingRecord.increment();
            return existingRecord;
        });
        
        if (record.getAttempts() >= maxAttempts && !record.isLocked()) {
            record.lock();
            log.error("User {} locked out after {} failed attempts", username, maxAttempts);
        }
    }
    
    /**
     * Checks if a user account is locked
     */
    public boolean isBlocked(String username) {
        AttemptRecord record = attempts.get(username);
        
        if (record == null) {
            return false;
        }
        
        // Check if lockout has expired
        if (record.isLocked() && record.hasLockoutExpired(lockoutDurationMinutes)) {
            log.info("Lockout expired for user: {}", username);
            attempts.remove(username);
            return false;
        }
        
        return record.isLocked();
    }
    
    /**
     * Gets the number of remaining attempts before lockout
     */
    public int getRemainingAttempts(String username) {
        AttemptRecord record = attempts.get(username);
        
        if (record == null) {
            return maxAttempts;
        }
        
        if (record.isLocked()) {
            return 0;
        }
        
        return Math.max(0, maxAttempts - record.getAttempts());
    }
    
    /**
     * Gets the lockout expiration time for a user
     */
    public Instant getLockoutExpiration(String username) {
        AttemptRecord record = attempts.get(username);
        
        if (record == null || !record.isLocked()) {
            return null;
        }
        
        return record.getLockedAt().plus(lockoutDurationMinutes, ChronoUnit.MINUTES);
    }
    
    /**
     * Clears all login attempt records (for admin use)
     */
    public void clearAllAttempts() {
        attempts.clear();
        log.info("All login attempt records cleared");
    }
    
    /**
     * Clears login attempt record for a specific user (for admin use)
     */
    public void clearAttempts(String username) {
        attempts.remove(username);
        log.info("Login attempt record cleared for user: {}", username);
    }
    
    /**
     * Internal class to track login attempts
     */
    private static class AttemptRecord {
        private final AtomicInteger attempts;
        private volatile boolean locked;
        private volatile Instant lockedAt;
        
        public AttemptRecord() {
            this.attempts = new AtomicInteger(1);
            this.locked = false;
            this.lockedAt = null;
        }
        
        public void increment() {
            attempts.incrementAndGet();
        }
        
        public int getAttempts() {
            return attempts.get();
        }
        
        public boolean isLocked() {
            return locked;
        }
        
        public void lock() {
            this.locked = true;
            this.lockedAt = Instant.now();
        }
        
        public Instant getLockedAt() {
            return lockedAt;
        }
        
        public boolean hasLockoutExpired(int lockoutDurationMinutes) {
            if (!locked || lockedAt == null) {
                return false;
            }
            
            return Instant.now().isAfter(lockedAt.plus(lockoutDurationMinutes, ChronoUnit.MINUTES));
        }
    }
}