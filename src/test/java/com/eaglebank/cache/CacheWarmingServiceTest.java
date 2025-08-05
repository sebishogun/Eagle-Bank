package com.eaglebank.cache;

import com.eaglebank.entity.User;
import com.eaglebank.repository.AccountRepository;
import com.eaglebank.repository.UserRepository;
import com.eaglebank.service.AccountService;
import com.eaglebank.service.UserService;
import com.eaglebank.util.UuidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CacheWarmingServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private AccountRepository accountRepository;
    
    @Mock
    private UserService userService;
    
    @Mock
    private AccountService accountService;
    
    @InjectMocks
    private CacheWarmingService cacheWarmingService;
    
    private List<User> testUsers;
    
    @BeforeEach
    void setUp() {
        User user1 = User.builder()
                .id(UuidGenerator.generateUuidV7())
                .email("user1@example.com")
                .firstName("User")
                .lastName("One")
                .build();
        
        User user2 = User.builder()
                .id(UuidGenerator.generateUuidV7())
                .email("user2@example.com")
                .firstName("User")
                .lastName("Two")
                .build();
        
        testUsers = Arrays.asList(user1, user2);
    }
    
    @Test
    @DisplayName("Should warm caches on application startup")
    void shouldWarmCachesOnStartup() {
        // Mock repository responses
        Page<User> userPage = new PageImpl<>(testUsers);
        when(userRepository.findAll(any(PageRequest.class))).thenReturn(userPage);
        
        // Execute cache warming
        ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
        cacheWarmingService.warmCachesOnStartup();
        
        // Allow async execution to complete
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Verify user service was called for each user
        verify(userService, times(2)).getUserById(any(UUID.class));
        
        // Verify account service was called
        verify(accountService, atLeast(1)).getUserAccounts(any(UUID.class), any(PageRequest.class));
    }
    
    @Test
    @DisplayName("Should handle errors during cache warming gracefully")
    void shouldHandleErrorsGracefully() {
        // Mock repository to throw exception
        when(userRepository.findAll(any(PageRequest.class)))
                .thenThrow(new RuntimeException("Database error"));
        
        // Should not throw exception
        assertDoesNotThrow(() -> cacheWarmingService.warmCachesOnStartup());
    }
    
    @Test
    @DisplayName("Should warm cache for specific user")
    void shouldWarmCacheForSpecificUser() {
        UUID userId = UuidGenerator.generateUuidV7();
        String userIdString = userId.toString();
        
        // Execute specific user warming
        cacheWarmingService.warmSpecificUser(userIdString);
        
        // Verify services were called
        verify(userService).getUserById(userId);
        verify(accountService).getUserAccounts(eq(userId), any(PageRequest.class));
    }
    
    @Test
    @DisplayName("Should handle invalid user ID format")
    void shouldHandleInvalidUserIdFormat() {
        String invalidUserId = "not-a-valid-uuid";
        
        // Should not throw exception
        assertDoesNotThrow(() -> cacheWarmingService.warmSpecificUser(invalidUserId));
        
        // Services should not be called
        verify(userService, never()).getUserById(any(UUID.class));
        verify(accountService, never()).getUserAccounts(any(UUID.class), any(PageRequest.class));
    }
    
    @Test
    @DisplayName("Should continue warming other users if one fails")
    void shouldContinueWarmingIfOneFails() {
        // Mock repository responses
        Page<User> userPage = new PageImpl<>(testUsers);
        when(userRepository.findAll(any(PageRequest.class))).thenReturn(userPage);
        
        // Make first user fail, second succeed
        when(userService.getUserById(testUsers.get(0).getId()))
                .thenThrow(new RuntimeException("User not found"));
        when(userService.getUserById(testUsers.get(1).getId()))
                .thenReturn(null);
        
        // Execute cache warming
        cacheWarmingService.warmCachesOnStartup();
        
        // Allow async execution to complete
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Verify both users were attempted
        verify(userService).getUserById(testUsers.get(0).getId());
        verify(userService).getUserById(testUsers.get(1).getId());
    }
}