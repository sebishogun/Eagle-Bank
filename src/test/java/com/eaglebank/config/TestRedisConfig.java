package com.eaglebank.config;

import com.eaglebank.service.RefreshTokenService;
import com.eaglebank.service.TokenBlacklistService;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@TestConfiguration
@Profile("test")
public class TestRedisConfig {
    
    @Bean
    @Primary
    public RedisTemplate<String, String> redisTemplate() {
        RedisTemplate<String, String> template = Mockito.mock(RedisTemplate.class);
        
        // Mock ValueOperations
        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        doNothing().when(valueOps).set(anyString(), anyString());
        doNothing().when(valueOps).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        
        // Mock SetOperations
        SetOperations<String, String> setOps = Mockito.mock(SetOperations.class);
        when(template.opsForSet()).thenReturn(setOps);
        when(setOps.add(anyString(), any())).thenReturn(1L);
        when(setOps.members(anyString())).thenReturn(java.util.Collections.emptySet());
        
        // Mock other common operations
        when(template.hasKey(anyString())).thenReturn(false);
        when(template.delete(anyString())).thenReturn(true);
        when(template.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        
        return template;
    }
    
    @Bean
    @Primary
    public RedisTemplate<String, Object> objectRedisTemplate() {
        RedisTemplate<String, Object> template = Mockito.mock(RedisTemplate.class);
        
        // Mock ValueOperations
        ValueOperations<String, Object> valueOps = Mockito.mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        doNothing().when(valueOps).set(anyString(), any());
        doNothing().when(valueOps).set(anyString(), any(), anyLong(), any(TimeUnit.class));
        
        // Mock SetOperations
        SetOperations<String, Object> setOps = Mockito.mock(SetOperations.class);
        when(template.opsForSet()).thenReturn(setOps);
        when(setOps.add(anyString(), any())).thenReturn(1L);
        when(setOps.members(anyString())).thenReturn(java.util.Collections.emptySet());
        
        // Mock other common operations
        when(template.hasKey(anyString())).thenReturn(false);
        when(template.delete(anyString())).thenReturn(true);
        when(template.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        
        return template;
    }
    
    @Bean
    @Primary
    public TokenBlacklistService tokenBlacklistService() {
        // Return a mock that doesn't actually use Redis
        TokenBlacklistService mock = Mockito.mock(TokenBlacklistService.class);
        when(mock.isBlacklisted(anyString())).thenReturn(false);
        doNothing().when(mock).blacklistToken(anyString(), any(Date.class), any(UUID.class));
        doNothing().when(mock).trackUserToken(any(UUID.class), anyString(), any(Date.class));
        doNothing().when(mock).revokeAllUserTokens(any(UUID.class));
        return mock;
    }
    
    @Bean
    @Primary
    public RefreshTokenService refreshTokenService() {
        // Return a mock refresh token service for tests
        RefreshTokenService mock = Mockito.mock(RefreshTokenService.class);
        when(mock.createRefreshToken(any(UUID.class))).thenReturn(UUID.randomUUID().toString());
        when(mock.validateRefreshToken(anyString())).thenReturn(null);
        doNothing().when(mock).revokeRefreshToken(anyString());
        doNothing().when(mock).revokeAllUserTokens(any(UUID.class));
        return mock;
    }
}