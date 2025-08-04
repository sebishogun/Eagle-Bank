package com.eaglebank.metrics;

import com.eaglebank.entity.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TransactionMetricsCollector implements MetricsCollector {
    
    private final Map<Transaction.TransactionType, AtomicInteger> transactionCounts = new ConcurrentHashMap<>();
    private final Map<Transaction.TransactionType, AtomicLong> transactionVolumes = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Long> processingTimes = new ConcurrentLinkedQueue<>();
    private final Map<MetricWindow, TimeWindowMetrics> windowMetrics = new ConcurrentHashMap<>();
    private final AtomicInteger totalTransactions = new AtomicInteger(0);
    private final AtomicLong totalVolume = new AtomicLong(0);
    
    public TransactionMetricsCollector() {
        // Initialize counters
        for (Transaction.TransactionType type : Transaction.TransactionType.values()) {
            transactionCounts.put(type, new AtomicInteger(0));
            transactionVolumes.put(type, new AtomicLong(0));
        }
        
        // Initialize time windows
        for (MetricWindow window : MetricWindow.values()) {
            windowMetrics.put(window, new TimeWindowMetrics(window));
        }
    }
    
    @Override
    public String getMetricName() {
        return "transaction_metrics";
    }
    
    @Override
    public Map<String, Object> collect() {
        Map<String, Object> metrics = new HashMap<>();
        
        // Basic counts
        metrics.put("total_transactions", totalTransactions.get());
        metrics.put("total_volume", totalVolume.get());
        
        // Type-specific metrics
        Map<String, Object> byType = new HashMap<>();
        for (Transaction.TransactionType type : Transaction.TransactionType.values()) {
            Map<String, Object> typeMetrics = new HashMap<>();
            typeMetrics.put("count", transactionCounts.get(type).get());
            typeMetrics.put("volume", transactionVolumes.get(type).get());
            byType.put(type.name().toLowerCase(), typeMetrics);
        }
        metrics.put("by_type", byType);
        
        // Processing time metrics
        metrics.put("processing_times", calculateProcessingTimeStats());
        
        // Time window metrics
        Map<String, Object> windows = new HashMap<>();
        for (Map.Entry<MetricWindow, TimeWindowMetrics> entry : windowMetrics.entrySet()) {
            windows.put(entry.getKey().name().toLowerCase(), entry.getValue().getMetrics());
        }
        metrics.put("time_windows", windows);
        
        return metrics;
    }
    
    @Override
    public void reset() {
        transactionCounts.values().forEach(counter -> counter.set(0));
        transactionVolumes.values().forEach(counter -> counter.set(0));
        processingTimes.clear();
        windowMetrics.values().forEach(TimeWindowMetrics::reset);
        totalTransactions.set(0);
        totalVolume.set(0);
        log.info("Transaction metrics reset");
    }
    
    public void recordTransaction(Transaction.TransactionType type, BigDecimal amount, long processingTimeMs) {
        // Update counters
        transactionCounts.get(type).incrementAndGet();
        transactionVolumes.get(type).addAndGet(amount.longValue());
        totalTransactions.incrementAndGet();
        totalVolume.addAndGet(amount.longValue());
        
        // Record processing time
        processingTimes.offer(processingTimeMs);
        if (processingTimes.size() > 10000) {
            processingTimes.poll(); // Keep only recent 10k samples
        }
        
        // Update time windows
        windowMetrics.values().forEach(window -> 
            window.record(type, amount, processingTimeMs)
        );
    }
    
    private Map<String, Object> calculateProcessingTimeStats() {
        Map<String, Object> stats = new HashMap<>();
        
        List<Long> times = new ArrayList<>(processingTimes);
        if (times.isEmpty()) {
            stats.put("count", 0);
            return stats;
        }
        
        Collections.sort(times);
        
        stats.put("count", times.size());
        stats.put("min", times.get(0));
        stats.put("max", times.get(times.size() - 1));
        stats.put("avg", times.stream().mapToLong(Long::longValue).average().orElse(0.0));
        stats.put("p50", getPercentile(times, 50));
        stats.put("p95", getPercentile(times, 95));
        stats.put("p99", getPercentile(times, 99));
        
        return stats;
    }
    
    private long getPercentile(List<Long> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) return 0;
        int index = (int) Math.ceil(sortedValues.size() * percentile / 100.0) - 1;
        return sortedValues.get(Math.max(0, Math.min(index, sortedValues.size() - 1)));
    }
    
    private static class TimeWindowMetrics {
        private final MetricWindow window;
        private final ConcurrentLinkedQueue<TimedTransaction> transactions = new ConcurrentLinkedQueue<>();
        
        TimeWindowMetrics(MetricWindow window) {
            this.window = window;
        }
        
        void record(Transaction.TransactionType type, BigDecimal amount, long processingTimeMs) {
            // Add new transaction
            transactions.offer(new TimedTransaction(
                LocalDateTime.now(),
                type,
                amount,
                processingTimeMs
            ));
            
            // Remove old transactions outside window
            LocalDateTime cutoff = LocalDateTime.now().minusSeconds(window.getSeconds());
            transactions.removeIf(t -> t.timestamp.isBefore(cutoff));
        }
        
        Map<String, Object> getMetrics() {
            Map<String, Object> metrics = new HashMap<>();
            
            List<TimedTransaction> current = new ArrayList<>(transactions);
            metrics.put("transaction_count", current.size());
            
            if (!current.isEmpty()) {
                // Calculate rate
                double ratePerSecond = current.size() / (double) window.getSeconds();
                metrics.put("rate_per_second", ratePerSecond);
                
                // Calculate volume
                BigDecimal totalVolume = current.stream()
                    .map(t -> t.amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                metrics.put("total_volume", totalVolume);
                
                // Type breakdown
                Map<String, Long> typeCount = current.stream()
                    .collect(Collectors.groupingBy(
                        t -> t.type.name().toLowerCase(),
                        Collectors.counting()
                    ));
                metrics.put("by_type", typeCount);
                
                // Processing time stats
                List<Long> times = current.stream()
                    .map(t -> t.processingTimeMs)
                    .sorted()
                    .collect(Collectors.toList());
                
                if (!times.isEmpty()) {
                    Map<String, Object> timeStats = new HashMap<>();
                    timeStats.put("avg", times.stream().mapToLong(Long::longValue).average().orElse(0.0));
                    timeStats.put("p95", times.get((int) (times.size() * 0.95)));
                    metrics.put("processing_times", timeStats);
                }
            }
            
            return metrics;
        }
        
        void reset() {
            transactions.clear();
        }
        
        private static class TimedTransaction {
            final LocalDateTime timestamp;
            final Transaction.TransactionType type;
            final BigDecimal amount;
            final long processingTimeMs;
            
            TimedTransaction(LocalDateTime timestamp, Transaction.TransactionType type, 
                           BigDecimal amount, long processingTimeMs) {
                this.timestamp = timestamp;
                this.type = type;
                this.amount = amount;
                this.processingTimeMs = processingTimeMs;
            }
        }
    }
}