package com.eaglebank.cache;

import com.eaglebank.metrics.MetricsDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class CacheStatisticsService {
    
    private final CacheManager cacheManager;
    private final Map<String, CacheStats> cacheStatsMap = new ConcurrentHashMap<>();
    
    public CacheStatisticsService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
        initializeCacheStats();
    }
    
    private void initializeCacheStats() {
        Collection<String> cacheNames = cacheManager.getCacheNames();
        for (String cacheName : cacheNames) {
            cacheStatsMap.put(cacheName, new CacheStats(cacheName));
        }
        log.info("Initialized cache statistics for {} caches", cacheNames.size());
    }
    
    public void recordCacheHit(String cacheName) {
        CacheStats stats = cacheStatsMap.get(cacheName);
        if (stats != null) {
            stats.recordHit();
        }
    }
    
    public void recordCacheMiss(String cacheName) {
        CacheStats stats = cacheStatsMap.get(cacheName);
        if (stats != null) {
            stats.recordMiss();
        }
    }
    
    public void recordCacheEviction(String cacheName) {
        CacheStats stats = cacheStatsMap.get(cacheName);
        if (stats != null) {
            stats.recordEviction();
        }
    }
    
    public Map<String, MetricsDto.CacheMetricsDto> getAllCacheStatistics() {
        Map<String, MetricsDto.CacheMetricsDto> allStats = new HashMap<>();
        
        for (Map.Entry<String, CacheStats> entry : cacheStatsMap.entrySet()) {
            String cacheName = entry.getKey();
            CacheStats stats = entry.getValue();
            
            allStats.put(cacheName, MetricsDto.CacheMetricsDto.builder()
                    .cacheName(cacheName)
                    .hits(stats.getHits())
                    .misses(stats.getMisses())
                    .evictions(stats.getEvictions())
                    .size(getCacheSize(cacheName))
                    .hitRate(stats.getHitRate())
                    .lastReset(stats.getLastReset())
                    .build());
        }
        
        return allStats;
    }
    
    public MetricsDto.CacheMetricsDto getCacheStatistics(String cacheName) {
        CacheStats stats = cacheStatsMap.get(cacheName);
        if (stats == null) {
            return null;
        }
        
        return MetricsDto.CacheMetricsDto.builder()
                .cacheName(cacheName)
                .hits(stats.getHits())
                .misses(stats.getMisses())
                .evictions(stats.getEvictions())
                .size(getCacheSize(cacheName))
                .hitRate(stats.getHitRate())
                .lastReset(stats.getLastReset())
                .build();
    }
    
    public void resetStatistics(String cacheName) {
        CacheStats stats = cacheStatsMap.get(cacheName);
        if (stats != null) {
            stats.reset();
            log.info("Reset statistics for cache: {}", cacheName);
        }
    }
    
    public void resetAllStatistics() {
        cacheStatsMap.values().forEach(CacheStats::reset);
        log.info("Reset all cache statistics");
    }
    
    private Long getCacheSize(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache instanceof ConcurrentMapCache) {
            ConcurrentMapCache mapCache = (ConcurrentMapCache) cache;
            return (long) mapCache.getNativeCache().size();
        }
        return 0L;
    }
    
    public Map<String, Object> getCacheSummary() {
        Map<String, Object> summary = new HashMap<>();
        
        long totalHits = cacheStatsMap.values().stream()
                .mapToLong(CacheStats::getHits)
                .sum();
        
        long totalMisses = cacheStatsMap.values().stream()
                .mapToLong(CacheStats::getMisses)
                .sum();
        
        long totalEvictions = cacheStatsMap.values().stream()
                .mapToLong(CacheStats::getEvictions)
                .sum();
        
        double overallHitRate = (totalHits + totalMisses) > 0 
                ? (double) totalHits / (totalHits + totalMisses) * 100 
                : 0.0;
        
        summary.put("total_caches", cacheStatsMap.size());
        summary.put("total_hits", totalHits);
        summary.put("total_misses", totalMisses);
        summary.put("total_evictions", totalEvictions);
        summary.put("overall_hit_rate", String.format("%.2f%%", overallHitRate));
        summary.put("cache_names", new ArrayList<>(cacheStatsMap.keySet()));
        
        return summary;
    }
    
    private static class CacheStats {
        private final String cacheName;
        private final AtomicLong hits = new AtomicLong(0);
        private final AtomicLong misses = new AtomicLong(0);
        private final AtomicLong evictions = new AtomicLong(0);
        private LocalDateTime lastReset = LocalDateTime.now();
        
        CacheStats(String cacheName) {
            this.cacheName = cacheName;
        }
        
        void recordHit() {
            hits.incrementAndGet();
        }
        
        void recordMiss() {
            misses.incrementAndGet();
        }
        
        void recordEviction() {
            evictions.incrementAndGet();
        }
        
        long getHits() {
            return hits.get();
        }
        
        long getMisses() {
            return misses.get();
        }
        
        long getEvictions() {
            return evictions.get();
        }
        
        double getHitRate() {
            long total = hits.get() + misses.get();
            return total > 0 ? (double) hits.get() / total * 100 : 0.0;
        }
        
        LocalDateTime getLastReset() {
            return lastReset;
        }
        
        void reset() {
            hits.set(0);
            misses.set(0);
            evictions.set(0);
            lastReset = LocalDateTime.now();
        }
    }
}