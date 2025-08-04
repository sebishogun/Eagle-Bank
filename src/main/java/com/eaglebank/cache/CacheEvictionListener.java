package com.eaglebank.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static com.eaglebank.config.CacheConfig.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheEvictionListener {
    
    private final CacheStatisticsService cacheStatisticsService;
    
    @Scheduled(fixedRate = 3600000) // Every hour
    @Caching(evict = {
        @CacheEvict(value = TRANSACTIONS_CACHE, allEntries = true),
        @CacheEvict(value = ACCOUNT_TRANSACTIONS_CACHE, allEntries = true)
    })
    public void evictStaleTransactionCaches() {
        log.info("Evicting stale transaction caches");
        cacheStatisticsService.recordCacheEviction(TRANSACTIONS_CACHE);
        cacheStatisticsService.recordCacheEviction(ACCOUNT_TRANSACTIONS_CACHE);
    }
    
    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    @Caching(evict = {
        @CacheEvict(value = USERS_CACHE, allEntries = true),
        @CacheEvict(value = ACCOUNTS_CACHE, allEntries = true),
        @CacheEvict(value = USER_ACCOUNTS_CACHE, allEntries = true)
    })
    public void performDailyCacheCleanup() {
        log.info("Performing daily cache cleanup");
        cacheStatisticsService.recordCacheEviction(USERS_CACHE);
        cacheStatisticsService.recordCacheEviction(ACCOUNTS_CACHE);
        cacheStatisticsService.recordCacheEviction(USER_ACCOUNTS_CACHE);
    }
    
    public void evictUserRelatedCaches(String userId) {
        log.debug("Evicting caches for user: {}", userId);
        // This would be called when user data changes significantly
    }
    
    public void evictAccountRelatedCaches(String accountId) {
        log.debug("Evicting caches for account: {}", accountId);
        // This would be called when account data changes significantly
    }
    
    @EventListener
    public void handleCacheEvictEvent(CacheEvictEvent event) {
        log.info("Cache evict event received for cache: {} with key: {}", 
            event.getCacheName(), event.getKey());
        cacheStatisticsService.recordCacheEviction(event.getCacheName());
    }
    
    public static class CacheEvictEvent {
        private final String cacheName;
        private final Object key;
        
        public CacheEvictEvent(String cacheName, Object key) {
            this.cacheName = cacheName;
            this.key = key;
        }
        
        public String getCacheName() {
            return cacheName;
        }
        
        public Object getKey() {
            return key;
        }
    }
}