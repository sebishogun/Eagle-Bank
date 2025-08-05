package com.eaglebank.service;

import com.eaglebank.entity.User;
import com.eaglebank.exception.UnauthorizedException;
import com.eaglebank.repository.UserRepository;
import com.eaglebank.util.UuidGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing JWT refresh tokens
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {
    
    private final UserRepository userRepository;
    
    @Value("${jwt.refresh.expiration:604800000}") // 7 days in milliseconds
    private long refreshTokenExpiration;
    
    // In production, this should be stored in Redis or database
    private final Map<String, RefreshTokenData> refreshTokenStore = new ConcurrentHashMap<>();
    
    /**
     * Creates a new refresh token for a user
     */
    @Transactional
    public String createRefreshToken(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        
        // Generate secure random token
        String token = UuidGenerator.generateUuidV4().toString();
        
        // Calculate expiration
        Instant expiresAt = Instant.now().plus(refreshTokenExpiration, ChronoUnit.MILLIS);
        
        // Store token data
        RefreshTokenData tokenData = new RefreshTokenData(userId, user.getEmail(), expiresAt);
        refreshTokenStore.put(token, tokenData);
        
        log.debug("Created refresh token for user: {}", user.getEmail());
        
        return token;
    }
    
    /**
     * Validates a refresh token and returns the associated user data
     */
    public RefreshTokenData validateRefreshToken(String token) {
        RefreshTokenData tokenData = refreshTokenStore.get(token);
        
        if (tokenData == null) {
            throw new UnauthorizedException("Invalid refresh token");
        }
        
        if (tokenData.isExpired()) {
            refreshTokenStore.remove(token);
            throw new UnauthorizedException("Refresh token has expired");
        }
        
        // Verify user still exists
        userRepository.findById(tokenData.getUserId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        
        return tokenData;
    }
    
    /**
     * Revokes a refresh token
     */
    public void revokeRefreshToken(String token) {
        if (refreshTokenStore.remove(token) != null) {
            log.debug("Revoked refresh token");
        }
    }
    
    /**
     * Revokes all refresh tokens for a user
     */
    public void revokeAllUserTokens(UUID userId) {
        refreshTokenStore.entrySet().removeIf(entry -> {
            if (entry.getValue().getUserId().equals(userId)) {
                log.debug("Revoked refresh token for user: {}", userId);
                return true;
            }
            return false;
        });
    }
    
    /**
     * Rotates a refresh token (creates new one and revokes old one)
     */
    @Transactional
    public String rotateRefreshToken(String oldToken) {
        RefreshTokenData tokenData = validateRefreshToken(oldToken);
        revokeRefreshToken(oldToken);
        return createRefreshToken(tokenData.getUserId());
    }
    
    /**
     * Cleans up expired tokens (should be called periodically)
     */
    public void cleanupExpiredTokens() {
        int removed = 0;
        var iterator = refreshTokenStore.entrySet().iterator();
        
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().isExpired()) {
                iterator.remove();
                removed++;
            }
        }
        
        if (removed > 0) {
            log.info("Cleaned up {} expired refresh tokens", removed);
        }
    }
    
    /**
     * Data class for refresh token information
     */
    public static class RefreshTokenData {
        private final UUID userId;
        private final String email;
        private final Instant expiresAt;
        
        public RefreshTokenData(UUID userId, String email, Instant expiresAt) {
            this.userId = userId;
            this.email = email;
            this.expiresAt = expiresAt;
        }
        
        public UUID getUserId() {
            return userId;
        }
        
        public String getEmail() {
            return email;
        }
        
        public Instant getExpiresAt() {
            return expiresAt;
        }
        
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}