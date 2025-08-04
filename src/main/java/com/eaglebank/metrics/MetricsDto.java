package com.eaglebank.metrics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricsDto {
    
    private LocalDateTime timestamp;
    private String metricType;
    private Map<String, Object> metrics;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemHealthDto {
        private String status;
        private LocalDateTime timestamp;
        private Long uptimeMs;
        private MemoryMetricsDto memory;
        private Map<String, Object> componentHealth;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemoryMetricsDto {
        private Long totalMb;
        private Long usedMb;
        private Long freeMb;
        private Long maxMb;
        private Double usagePercent;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionMetricsDto {
        private Long totalTransactions;
        private Long totalVolume;
        private Map<String, Object> byType;
        private Map<String, Object> processingTimes;
        private Map<String, Object> timeWindows;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CacheMetricsDto {
        private String cacheName;
        private Long hits;
        private Long misses;
        private Long evictions;
        private Long size;
        private Double hitRate;
        private LocalDateTime lastReset;
    }
}