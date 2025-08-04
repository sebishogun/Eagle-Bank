package com.eaglebank.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {
    
    private JwtTokenProvider jwtTokenProvider;
    private final String secret = "ThisIsAVeryLongSecretKeyForTestingPurposesOnlyAndShouldBeAtLeast256Bits";
    private final long expiration = 86400000; // 24 hours
    
    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "secret", secret);
        ReflectionTestUtils.setField(jwtTokenProvider, "expiration", expiration);
        jwtTokenProvider.init();
    }
    
    @Test
    void generateToken_ShouldCreateValidToken() {
        // Given
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        
        // When
        String token = jwtTokenProvider.generateToken(userId, email);
        
        // Then
        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
        assertThat(token.split("\\.")).hasSize(3); // JWT has 3 parts
    }
    
    @Test
    void getUserIdFromToken_ShouldExtractCorrectUserId() {
        // Given
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        String token = jwtTokenProvider.generateToken(userId, email);
        
        // When
        UUID extractedUserId = jwtTokenProvider.getUserIdFromToken(token);
        
        // Then
        assertThat(extractedUserId).isEqualTo(userId);
    }
    
    @Test
    void getEmailFromToken_ShouldExtractCorrectEmail() {
        // Given
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        String token = jwtTokenProvider.generateToken(userId, email);
        
        // When
        String extractedEmail = jwtTokenProvider.getEmailFromToken(token);
        
        // Then
        assertThat(extractedEmail).isEqualTo(email);
    }
    
    @Test
    void validateToken_ShouldReturnTrueForValidToken() {
        // Given
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        String token = jwtTokenProvider.generateToken(userId, email);
        
        // When
        boolean isValid = jwtTokenProvider.validateToken(token);
        
        // Then
        assertThat(isValid).isTrue();
    }
    
    @Test
    void validateToken_ShouldReturnFalseForInvalidToken() {
        // Given
        String invalidToken = "invalid.token.here";
        
        // When
        boolean isValid = jwtTokenProvider.validateToken(invalidToken);
        
        // Then
        assertThat(isValid).isFalse();
    }
    
    @Test
    void validateToken_ShouldReturnFalseForExpiredToken() {
        // Given
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "secret", secret);
        ReflectionTestUtils.setField(jwtTokenProvider, "expiration", -1000L); // Already expired
        jwtTokenProvider.init();
        
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        String token = jwtTokenProvider.generateToken(userId, email);
        
        // When
        boolean isValid = jwtTokenProvider.validateToken(token);
        
        // Then
        assertThat(isValid).isFalse();
    }
    
    @Test
    void getClaimsFromToken_ShouldReturnCorrectClaims() {
        // Given
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        String token = jwtTokenProvider.generateToken(userId, email);
        
        // When
        Claims claims = jwtTokenProvider.getClaimsFromToken(token);
        
        // Then
        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("email", String.class)).isEqualTo(email);
    }
}