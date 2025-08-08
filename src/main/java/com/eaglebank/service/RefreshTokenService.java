package com.eaglebank.service;

import com.eaglebank.entity.User;
import com.eaglebank.exception.UnauthorizedException;
import com.eaglebank.repository.UserRepository;
import com.eaglebank.util.UuidGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing JWT refresh tokens using Redis for persistence
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {
    
    private static final String REFRESH_TOKEN_PREFIX = "refresh:token:";
    private static final String USER_REFRESH_PREFIX = "refresh:user:";
    
    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${jwt.refresh.expiration:604800000}") // 7 days in milliseconds
    private long refreshTokenExpiration;
    
    @PostConstruct
    public void init() {
        // Register JavaTimeModule to handle Java 8 time types
        objectMapper.registerModule(new JavaTimeModule());
    }
    
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
        
        // Store token data in Redis
        RefreshTokenData tokenData = new RefreshTokenData(userId, user.getEmail(), expiresAt);
        String tokenKey = REFRESH_TOKEN_PREFIX + token;
        String userKey = USER_REFRESH_PREFIX + userId.toString();
        
        try {
            // Store the token data
            String tokenDataJson = objectMapper.writeValueAsString(tokenData);
            redisTemplate.opsForValue().set(tokenKey, tokenDataJson, refreshTokenExpiration, TimeUnit.MILLISECONDS);
            
            // Track user's refresh tokens for bulk revocation
            redisTemplate.opsForSet().add(userKey, token);
            redisTemplate.expire(userKey, Duration.ofMillis(refreshTokenExpiration));
            
            log.debug("Created refresh token for user: {}", user.getEmail());
        } catch (Exception e) {
            log.error("Error creating refresh token", e);
            throw new RuntimeException("Failed to create refresh token", e);
        }
        
        return token;
    }
    
    /**
     * Validates a refresh token and returns the associated user data
     */
    public RefreshTokenData validateRefreshToken(String token) {
        String tokenKey = REFRESH_TOKEN_PREFIX + token;
        String tokenDataJson = redisTemplate.opsForValue().get(tokenKey);
        
        if (tokenDataJson == null) {
            throw new UnauthorizedException("Invalid refresh token");
        }
        
        try {
            RefreshTokenData tokenData = objectMapper.readValue(tokenDataJson, RefreshTokenData.class);
            
            if (tokenData.isExpired()) {
                redisTemplate.delete(tokenKey);
                throw new UnauthorizedException("Refresh token has expired");
            }
            
            // Verify user still exists
            userRepository.findById(tokenData.getUserId())
                    .orElseThrow(() -> new UnauthorizedException("User not found"));
            
            return tokenData;
        } catch (Exception e) {
            if (e instanceof UnauthorizedException) {
                throw (UnauthorizedException) e;
            }
            log.error("Error validating refresh token", e);
            throw new UnauthorizedException("Invalid refresh token");
        }
    }
    
    /**
     * Revokes a refresh token
     */
    public void revokeRefreshToken(String token) {
        String tokenKey = REFRESH_TOKEN_PREFIX + token;
        String tokenDataJson = redisTemplate.opsForValue().get(tokenKey);
        
        if (tokenDataJson != null) {
            try {
                RefreshTokenData tokenData = objectMapper.readValue(tokenDataJson, RefreshTokenData.class);
                String userKey = USER_REFRESH_PREFIX + tokenData.getUserId().toString();
                
                // Remove from user's token set
                redisTemplate.opsForSet().remove(userKey, token);
                
                // Delete the token
                redisTemplate.delete(tokenKey);
                
                log.debug("Revoked refresh token");
            } catch (Exception e) {
                log.error("Error revoking refresh token", e);
            }
        }
    }
    
    /**
     * Revokes all refresh tokens for a user
     */
    public void revokeAllUserTokens(UUID userId) {
        if (userId == null) {
            return;
        }
        
        String userKey = USER_REFRESH_PREFIX + userId.toString();
        
        // Get all tokens for this user
        var tokens = redisTemplate.opsForSet().members(userKey);
        if (tokens != null && !tokens.isEmpty()) {
            // Delete each token
            for (String token : tokens) {
                String tokenKey = REFRESH_TOKEN_PREFIX + token;
                redisTemplate.delete(tokenKey);
            }
            
            log.info("Revoked {} refresh tokens for user {}", tokens.size(), userId);
        }
        
        // Clean up the user key
        redisTemplate.delete(userKey);
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
     * Cleans up expired tokens (Redis TTL handles this automatically)
     */
    public void cleanupExpiredTokens() {
        // Redis handles expiration automatically with TTL
        log.debug("Redis TTL handles token expiration automatically");
    }
    
    /**
     * Data class for refresh token information
     */
    public static class RefreshTokenData {
        private UUID userId;
        private String email;
        private Instant expiresAt;
        
        // Default constructor for Jackson
        public RefreshTokenData() {
        }
        
        public RefreshTokenData(UUID userId, String email, Instant expiresAt) {
            this.userId = userId;
            this.email = email;
            this.expiresAt = expiresAt;
        }
        
        public UUID getUserId() {
            return userId;
        }
        
        public void setUserId(UUID userId) {
            this.userId = userId;
        }
        
        public String getEmail() {
            return email;
        }
        
        public void setEmail(String email) {
            this.email = email;
        }
        
        public Instant getExpiresAt() {
            return expiresAt;
        }
        
        public void setExpiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
        }
        
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}