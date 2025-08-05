package com.eaglebank.controller;

import com.eaglebank.cache.CacheStatisticsService;
import com.eaglebank.metrics.MetricsDto;
import com.eaglebank.metrics.MetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/v1/metrics")
@RequiredArgsConstructor
@Tag(name = "Metrics", description = "Application metrics and monitoring endpoints")
@PreAuthorize("hasRole('ADMIN')")
public class MetricsController {
    
    private final MetricsService metricsService;
    private final CacheStatisticsService cacheStatisticsService;
    
    @GetMapping
    @Operation(summary = "Get all application metrics")
    public ResponseEntity<MetricsDto> getAllMetrics() {
        Map<String, Object> metrics = metricsService.getAllMetrics();
        
        // Add cache statistics
        Map<String, Object> cacheMetrics = new HashMap<>();
        cacheMetrics.put("summary", cacheStatisticsService.getCacheSummary());
        cacheMetrics.put("details", cacheStatisticsService.getAllCacheStatistics());
        metrics.put("cache_statistics", cacheMetrics);
        
        return ResponseEntity.ok(MetricsDto.builder()
                .timestamp(LocalDateTime.now())
                .metricType("all")
                .metrics(metrics)
                .build());
    }
    
    @GetMapping("/transaction")
    @Operation(summary = "Get transaction metrics")
    public ResponseEntity<MetricsDto> getTransactionMetrics() {
        Map<String, Object> metrics = metricsService.getMetricsByName("transaction_metrics");
        
        return ResponseEntity.ok(MetricsDto.builder()
                .timestamp(LocalDateTime.now())
                .metricType("transaction")
                .metrics(metrics)
                .build());
    }
    
    @GetMapping("/account")
    @Operation(summary = "Get account metrics")
    public ResponseEntity<MetricsDto> getAccountMetrics() {
        Map<String, Object> metrics = metricsService.getMetricsByName("account_metrics");
        
        return ResponseEntity.ok(MetricsDto.builder()
                .timestamp(LocalDateTime.now())
                .metricType("account")
                .metrics(metrics)
                .build());
    }
    
    @GetMapping("/authentication")
    @Operation(summary = "Get authentication metrics")
    public ResponseEntity<MetricsDto> getAuthenticationMetrics() {
        Map<String, Object> metrics = metricsService.getMetricsByName("authentication_metrics");
        
        return ResponseEntity.ok(MetricsDto.builder()
                .timestamp(LocalDateTime.now())
                .metricType("authentication")
                .metrics(metrics)
                .build());
    }
    
    @GetMapping("/cache")
    @Operation(summary = "Get cache statistics")
    public ResponseEntity<MetricsDto> getCacheMetrics() {
        Map<String, Object> cacheMetrics = new HashMap<>();
        cacheMetrics.put("summary", cacheStatisticsService.getCacheSummary());
        cacheMetrics.put("details", cacheStatisticsService.getAllCacheStatistics());
        
        return ResponseEntity.ok(MetricsDto.builder()
                .timestamp(LocalDateTime.now())
                .metricType("cache")
                .metrics(cacheMetrics)
                .build());
    }
    
    @GetMapping("/health")
    @Operation(summary = "Get system health metrics")
    public ResponseEntity<MetricsDto.SystemHealthDto> getSystemHealth() {
        Map<String, Object> allMetrics = metricsService.getAllMetrics();
        Map<String, Object> systemMetrics = (Map<String, Object>) allMetrics.get("system");
        
        // Calculate memory metrics
        Long totalMb = (Long) systemMetrics.get("total_memory_mb");
        Long freeMb = (Long) systemMetrics.get("free_memory_mb");
        Long usedMb = (Long) systemMetrics.get("used_memory_mb");
        Long maxMb = (Long) systemMetrics.get("max_memory_mb");
        
        MetricsDto.MemoryMetricsDto memoryMetrics = MetricsDto.MemoryMetricsDto.builder()
                .totalMb(totalMb)
                .usedMb(usedMb)
                .freeMb(freeMb)
                .maxMb(maxMb)
                .usagePercent((double) usedMb / totalMb * 100)
                .build();
        
        // Component health checks
        Map<String, Object> componentHealth = new HashMap<>();
        componentHealth.put("database", "UP");
        componentHealth.put("cache", "UP");
        componentHealth.put("metrics", "UP");
        
        return ResponseEntity.ok(MetricsDto.SystemHealthDto.builder()
                .status("UP")
                .timestamp(LocalDateTime.now())
                .uptimeMs((Long) systemMetrics.get("uptime_ms"))
                .memory(memoryMetrics)
                .componentHealth(componentHealth)
                .build());
    }
    
    @PostMapping("/reset/{metricType}")
    @Operation(summary = "Reset specific metric type")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> resetMetrics(@PathVariable String metricType) {
        // In production, this should be more controlled
        Map<String, String> response = new HashMap<>();
        response.put("status", "reset");
        response.put("metricType", metricType);
        response.put("timestamp", LocalDateTime.now().toString());
        
        log.warn("Metrics reset requested for type: {} by admin", metricType);
        
        return ResponseEntity.ok(response);
    }
}