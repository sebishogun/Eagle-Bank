package com.eaglebank.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing JWT token blacklist in Redis
 * Blacklisted tokens are stored until their expiration time
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {
    
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final String USER_TOKENS_PREFIX = "jwt:user:";
    
    private final RedisTemplate<String, String> redisTemplate;
    
    /**
     * Track a token for a user (for bulk revocation)
     * @param userId The user ID
     * @param token The JWT token
     * @param expirationDate The token's expiration date
     */
    public void trackUserToken(UUID userId, String token, Date expirationDate) {
        if (userId == null || token == null) {
            return;
        }
        
        String userKey = USER_TOKENS_PREFIX + userId.toString();
        
        // Add token to user's token set
        redisTemplate.opsForSet().add(userKey, token);
        
        // Set expiration for the user key
        long ttlMillis = expirationDate.getTime() - System.currentTimeMillis();
        if (ttlMillis > 0) {
            redisTemplate.expire(userKey, Duration.ofMillis(ttlMillis));
        }
        
        log.debug("Tracked token for user {}", userId);
    }
    
    /**
     * Add a token to the blacklist
     * @param token The JWT token to blacklist
     * @param expirationDate The token's expiration date
     * @param userId The user ID associated with the token
     */
    public void blacklistToken(String token, Date expirationDate, UUID userId) {
        if (token == null || expirationDate == null) {
            return;
        }
        
        long ttlMillis = expirationDate.getTime() - System.currentTimeMillis();
        if (ttlMillis <= 0) {
            // Token already expired, no need to blacklist
            return;
        }
        
        String key = BLACKLIST_PREFIX + token;
        String userKey = USER_TOKENS_PREFIX + userId.toString();
        
        // Store token in blacklist with TTL
        redisTemplate.opsForValue().set(key, userId.toString(), ttlMillis, TimeUnit.MILLISECONDS);
        
        // Also track by user for bulk revocation
        redisTemplate.opsForSet().add(userKey, token);
        redisTemplate.expire(userKey, Duration.ofDays(7));
        
        log.debug("Token blacklisted for user {} with TTL {} ms", userId, ttlMillis);
    }
    
    /**
     * Check if a token is blacklisted
     * @param token The JWT token to check
     * @return true if the token is blacklisted, false otherwise
     */
    public boolean isBlacklisted(String token) {
        if (token == null) {
            return false;
        }
        
        String key = BLACKLIST_PREFIX + token;
        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }
    
    /**
     * Revoke all tokens for a specific user
     * Used when password is changed or account is compromised
     * @param userId The user ID whose tokens should be revoked
     */
    public void revokeAllUserTokens(UUID userId) {
        if (userId == null) {
            return;
        }
        
        String userKey = USER_TOKENS_PREFIX + userId.toString();
        
        // Get all tokens for this user
        var tokens = redisTemplate.opsForSet().members(userKey);
        if (tokens != null && !tokens.isEmpty()) {
            // Blacklist each token
            for (String token : tokens) {
                // Set with max TTL since we don't have expiration info
                String key = BLACKLIST_PREFIX + token;
                redisTemplate.opsForValue().set(key, userId.toString(), 7, TimeUnit.DAYS);
            }
            
            log.info("Revoked {} tokens for user {}", tokens.size(), userId);
        }
        
        // Clean up the user key
        redisTemplate.delete(userKey);
    }
    
    /**
     * Remove a specific token from blacklist (for testing or admin purposes)
     * @param token The token to remove from blacklist
     */
    public void removeFromBlacklist(String token) {
        if (token == null) {
            return;
        }
        
        String key = BLACKLIST_PREFIX + token;
        Boolean deleted = redisTemplate.delete(key);
        
        if (Boolean.TRUE.equals(deleted)) {
            log.debug("Token removed from blacklist");
        }
    }
    
    /**
     * Get the count of blacklisted tokens (for monitoring)
     * @return The number of blacklisted tokens
     */
    public Long getBlacklistedTokenCount() {
        var keys = redisTemplate.keys(BLACKLIST_PREFIX + "*");
        return keys != null ? (long) keys.size() : 0L;
    }
}