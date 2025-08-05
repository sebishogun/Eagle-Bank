package com.eaglebank.cache;

import com.eaglebank.metrics.MetricsDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CacheStatisticsServiceTest {
    
    @Mock
    private CacheManager cacheManager;
    
    private CacheStatisticsService statisticsService;
    
    @BeforeEach
    void setUp() {
        Collection<String> cacheNames = Arrays.asList("users", "accounts", "transactions");
        when(cacheManager.getCacheNames()).thenReturn(cacheNames);
        
        statisticsService = new CacheStatisticsService(cacheManager);
    }
    
    @Test
    @DisplayName("Should record cache hit")
    void shouldRecordCacheHit() {
        statisticsService.recordCacheHit("users");
        
        MetricsDto.CacheMetricsDto metrics = statisticsService.getCacheStatistics("users");
        
        assertNotNull(metrics);
        assertEquals("users", metrics.getCacheName());
        assertEquals(1L, metrics.getHits());
        assertEquals(0L, metrics.getMisses());
        assertEquals(100.0, metrics.getHitRate());
    }
    
    @Test
    @DisplayName("Should record cache miss")
    void shouldRecordCacheMiss() {
        statisticsService.recordCacheMiss("accounts");
        
        MetricsDto.CacheMetricsDto metrics = statisticsService.getCacheStatistics("accounts");
        
        assertNotNull(metrics);
        assertEquals("accounts", metrics.getCacheName());
        assertEquals(0L, metrics.getHits());
        assertEquals(1L, metrics.getMisses());
        assertEquals(0.0, metrics.getHitRate());
    }
    
    @Test
    @DisplayName("Should record cache eviction")
    void shouldRecordCacheEviction() {
        statisticsService.recordCacheEviction("transactions");
        
        MetricsDto.CacheMetricsDto metrics = statisticsService.getCacheStatistics("transactions");
        
        assertNotNull(metrics);
        assertEquals(1L, metrics.getEvictions());
    }
    
    @Test
    @DisplayName("Should calculate hit rate correctly")
    void shouldCalculateHitRateCorrectly() {
        // Record mixed hits and misses
        statisticsService.recordCacheHit("users");
        statisticsService.recordCacheHit("users");
        statisticsService.recordCacheHit("users");
        statisticsService.recordCacheMiss("users");
        
        MetricsDto.CacheMetricsDto metrics = statisticsService.getCacheStatistics("users");
        
        assertEquals(3L, metrics.getHits());
        assertEquals(1L, metrics.getMisses());
        assertEquals(75.0, metrics.getHitRate());
    }
    
    @Test
    @DisplayName("Should get all cache statistics")
    void shouldGetAllCacheStatistics() {
        // Record some activity
        statisticsService.recordCacheHit("users");
        statisticsService.recordCacheMiss("accounts");
        statisticsService.recordCacheEviction("transactions");
        
        Map<String, MetricsDto.CacheMetricsDto> allStats = statisticsService.getAllCacheStatistics();
        
        assertNotNull(allStats);
        assertEquals(3, allStats.size());
        assertTrue(allStats.containsKey("users"));
        assertTrue(allStats.containsKey("accounts"));
        assertTrue(allStats.containsKey("transactions"));
    }
    
    @Test
    @DisplayName("Should reset statistics for specific cache")
    void shouldResetStatisticsForSpecificCache() {
        // Record activity
        statisticsService.recordCacheHit("users");
        statisticsService.recordCacheMiss("users");
        
        // Verify stats exist
        MetricsDto.CacheMetricsDto beforeReset = statisticsService.getCacheStatistics("users");
        assertEquals(1L, beforeReset.getHits());
        assertEquals(1L, beforeReset.getMisses());
        
        // Reset
        statisticsService.resetStatistics("users");
        
        // Verify stats are cleared
        MetricsDto.CacheMetricsDto afterReset = statisticsService.getCacheStatistics("users");
        assertEquals(0L, afterReset.getHits());
        assertEquals(0L, afterReset.getMisses());
        assertNotNull(afterReset.getLastReset());
    }
    
    @Test
    @DisplayName("Should reset all statistics")
    void shouldResetAllStatistics() {
        // Record activity on multiple caches
        statisticsService.recordCacheHit("users");
        statisticsService.recordCacheHit("accounts");
        statisticsService.recordCacheHit("transactions");
        
        // Reset all
        statisticsService.resetAllStatistics();
        
        // Verify all are cleared
        Map<String, MetricsDto.CacheMetricsDto> allStats = statisticsService.getAllCacheStatistics();
        allStats.values().forEach(stats -> {
            assertEquals(0L, stats.getHits());
            assertEquals(0L, stats.getMisses());
            assertEquals(0L, stats.getEvictions());
        });
    }
    
    @Test
    @DisplayName("Should get cache summary")
    void shouldGetCacheSummary() {
        // Record various activities
        statisticsService.recordCacheHit("users");
        statisticsService.recordCacheHit("users");
        statisticsService.recordCacheHit("accounts");
        statisticsService.recordCacheMiss("accounts");
        statisticsService.recordCacheMiss("transactions");
        statisticsService.recordCacheEviction("users");
        
        Map<String, Object> summary = statisticsService.getCacheSummary();
        
        assertNotNull(summary);
        assertEquals(3, summary.get("total_caches"));
        assertEquals(3L, summary.get("total_hits"));
        assertEquals(2L, summary.get("total_misses"));
        assertEquals(1L, summary.get("total_evictions"));
        assertEquals("60.00%", summary.get("overall_hit_rate"));
        assertTrue(((Collection<?>) summary.get("cache_names")).contains("users"));
    }
    
    @Test
    @DisplayName("Should handle cache size for ConcurrentMapCache")
    void shouldHandleCacheSizeForConcurrentMapCache() {
        ConcurrentMapCache mockCache = mock(ConcurrentMapCache.class);
        java.util.concurrent.ConcurrentHashMap<Object, Object> nativeCache = new java.util.concurrent.ConcurrentHashMap<>();
        nativeCache.put("key1", "value1");
        nativeCache.put("key2", "value2");
        
        when(mockCache.getNativeCache()).thenReturn(nativeCache);
        when(cacheManager.getCache("users")).thenReturn(mockCache);
        
        MetricsDto.CacheMetricsDto metrics = statisticsService.getCacheStatistics("users");
        
        assertEquals(2L, metrics.getSize());
    }
    
    @Test
    @DisplayName("Should return null for non-existent cache")
    void shouldReturnNullForNonExistentCache() {
        MetricsDto.CacheMetricsDto metrics = statisticsService.getCacheStatistics("non-existent");
        assertNull(metrics);
    }
}