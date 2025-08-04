package com.eaglebank.metrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {
    
    private final List<MetricsCollector> collectors;
    private final Map<String, Map<String, Object>> cachedMetrics = new ConcurrentHashMap<>();
    
    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("Initialized MetricsService with {} collectors", collectors.size());
        collectors.forEach(collector -> 
            log.info("Registered metrics collector: {}", collector.getMetricName())
        );
    }
    
    public Map<String, Object> getAllMetrics() {
        Map<String, Object> allMetrics = new HashMap<>();
        
        for (MetricsCollector collector : collectors) {
            try {
                Map<String, Object> collectorMetrics = collector.collect();
                allMetrics.put(collector.getMetricName(), collectorMetrics);
            } catch (Exception e) {
                log.error("Error collecting metrics from {}: {}", 
                    collector.getMetricName(), e.getMessage());
            }
        }
        
        // Add system metrics
        allMetrics.put("system", getSystemMetrics());
        
        return allMetrics;
    }
    
    public Map<String, Object> getMetricsByName(String metricName) {
        return collectors.stream()
            .filter(collector -> collector.getMetricName().equals(metricName))
            .findFirst()
            .map(MetricsCollector::collect)
            .orElse(new HashMap<>());
    }
    
    @Scheduled(fixedRate = 60000) // Every minute
    public void updateMetricsCache() {
        try {
            Map<String, Object> currentMetrics = getAllMetrics();
            
            // Update cache for each collector
            collectors.forEach(collector -> {
                String name = collector.getMetricName();
                Map<String, Object> metrics = collector.collect();
                cachedMetrics.put(name, metrics);
            });
            
            log.debug("Updated metrics cache with {} collectors", collectors.size());
        } catch (Exception e) {
            log.error("Error updating metrics cache: {}", e.getMessage());
        }
    }
    
    @Scheduled(cron = "0 0 0 * * *") // Daily at midnight
    public void resetDailyMetrics() {
        log.info("Resetting daily metrics");
        collectors.forEach(collector -> {
            try {
                collector.reset();
                log.info("Reset metrics for: {}", collector.getMetricName());
            } catch (Exception e) {
                log.error("Error resetting metrics for {}: {}", 
                    collector.getMetricName(), e.getMessage());
            }
        });
    }
    
    public Map<String, Object> getCachedMetrics() {
        return new HashMap<>(cachedMetrics);
    }
    
    private Map<String, Object> getSystemMetrics() {
        Map<String, Object> system = new HashMap<>();
        
        Runtime runtime = Runtime.getRuntime();
        system.put("total_memory_mb", runtime.totalMemory() / 1024 / 1024);
        system.put("free_memory_mb", runtime.freeMemory() / 1024 / 1024);
        system.put("used_memory_mb", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024);
        system.put("max_memory_mb", runtime.maxMemory() / 1024 / 1024);
        system.put("available_processors", runtime.availableProcessors());
        system.put("uptime_ms", System.currentTimeMillis());
        
        return system;
    }
}