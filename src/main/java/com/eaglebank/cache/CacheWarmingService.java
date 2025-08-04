package com.eaglebank.cache;

import com.eaglebank.entity.User;
import com.eaglebank.repository.AccountRepository;
import com.eaglebank.repository.UserRepository;
import com.eaglebank.service.AccountService;
import com.eaglebank.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheWarmingService {
    
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final UserService userService;
    private final AccountService accountService;
    
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void warmCachesOnStartup() {
        log.info("Starting cache warming process...");
        
        try {
            warmUserCache();
            warmAccountCache();
            warmFrequentlyAccessedData();
            
            log.info("Cache warming completed successfully");
        } catch (Exception e) {
            log.error("Error during cache warming: {}", e.getMessage(), e);
        }
    }
    
    private void warmUserCache() {
        log.debug("Warming user cache...");
        
        // Load most recently active users
        List<User> recentUsers = userRepository.findAll(
            PageRequest.of(0, 100)
        ).getContent();
        
        int warmed = 0;
        for (User user : recentUsers) {
            try {
                userService.getUserById(user.getId());
                warmed++;
            } catch (Exception e) {
                log.warn("Failed to warm cache for user {}: {}", user.getId(), e.getMessage());
            }
        }
        
        log.info("Warmed {} user cache entries", warmed);
    }
    
    private void warmAccountCache() {
        log.debug("Warming account cache...");
        
        // Load accounts with recent activity
        List<User> activeUsers = userRepository.findAll(
            PageRequest.of(0, 50)
        ).getContent();
        
        int warmed = 0;
        for (User user : activeUsers) {
            try {
                accountService.getUserAccounts(user.getId(), PageRequest.of(0, 10));
                warmed++;
            } catch (Exception e) {
                log.warn("Failed to warm account cache for user {}: {}", user.getId(), e.getMessage());
            }
        }
        
        log.info("Warmed {} account cache entries", warmed);
    }
    
    private void warmFrequentlyAccessedData() {
        log.debug("Warming frequently accessed data...");
        
        // This could be extended to warm:
        // - Popular transaction histories
        // - Frequently accessed reference data
        // - Common configuration values
        
        log.info("Completed warming frequently accessed data");
    }
    
    public void warmSpecificUser(String userId) {
        log.debug("Warming cache for specific user: {}", userId);
        
        try {
            // Warm user data
            userService.getUserById(java.util.UUID.fromString(userId));
            
            // Warm user's accounts
            accountService.getUserAccounts(
                java.util.UUID.fromString(userId), 
                PageRequest.of(0, 20)
            );
            
            log.info("Successfully warmed cache for user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to warm cache for user {}: {}", userId, e.getMessage());
        }
    }
}