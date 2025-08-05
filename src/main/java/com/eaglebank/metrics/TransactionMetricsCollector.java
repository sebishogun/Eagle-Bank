package com.eaglebank.metrics;

import com.eaglebank.entity.Transaction;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class TransactionMetricsCollector implements MetricsCollector {
    
    private final MeterRegistry meterRegistry;
    private final Map<Transaction.TransactionType, Counter> transactionCounters = new ConcurrentHashMap<>();
    private final Map<Transaction.TransactionType, Counter> volumeCounters = new ConcurrentHashMap<>();
    private final Timer processingTimer;
    
    // Keep track for collect() method compatibility
    private final Map<String, Double> lastKnownValues = new ConcurrentHashMap<>();
    
    public TransactionMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Initialize counters for each transaction type
        for (Transaction.TransactionType type : Transaction.TransactionType.values()) {
            String typeName = type.name().toLowerCase();
            
            transactionCounters.put(type, Counter.builder("transactions.count")
                    .tag("type", typeName)
                    .description("Number of transactions by type")
                    .register(meterRegistry));
            
            volumeCounters.put(type, Counter.builder("transactions.volume")
                    .tag("type", typeName)
                    .description("Volume of transactions by type")
                    .baseUnit("currency")
                    .register(meterRegistry));
        }
        
        // Initialize processing time timer
        this.processingTimer = Timer.builder("transactions.processing.time")
                .description("Transaction processing time")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(5))
                .register(meterRegistry);
    }
    
    @Override
    public String getMetricName() {
        return "transaction_metrics";
    }
    
    @Override
    public Map<String, Object> collect() {
        Map<String, Object> metrics = new HashMap<>();
        
        // Calculate total transactions from all counters
        double totalTransactions = 0;
        double totalVolume = 0;
        
        Map<String, Object> byType = new HashMap<>();
        for (Transaction.TransactionType type : Transaction.TransactionType.values()) {
            String typeName = type.name().toLowerCase();
            double count = transactionCounters.get(type).count();
            double volume = volumeCounters.get(type).count();
            
            totalTransactions += count;
            totalVolume += volume;
            
            Map<String, Object> typeMetrics = new HashMap<>();
            typeMetrics.put("count", (long) count);
            typeMetrics.put("volume", (long) volume);
            byType.put(typeName, typeMetrics);
        }
        
        metrics.put("total_transactions", (long) totalTransactions);
        metrics.put("total_volume", (long) totalVolume);
        metrics.put("by_type", byType);
        
        // Processing time metrics from timer
        HistogramSnapshot snapshot = processingTimer.takeSnapshot();
        
        Map<String, Object> processingTimes = new HashMap<>();
        processingTimes.put("count", snapshot.count());
        // Note: HistogramSnapshot doesn't have min(), using 0 as placeholder
        processingTimes.put("min", 0L);
        processingTimes.put("max", (long) snapshot.max(TimeUnit.MILLISECONDS));
        processingTimes.put("avg", snapshot.mean(TimeUnit.MILLISECONDS));
        
        // Get percentiles if available
        if (snapshot.percentileValues().length > 0) {
            processingTimes.put("p50", getPercentileValue(snapshot, 0.5));
            processingTimes.put("p95", getPercentileValue(snapshot, 0.95));
            processingTimes.put("p99", getPercentileValue(snapshot, 0.99));
        }
        
        metrics.put("processing_times", processingTimes);
        
        // Store values for potential future use
        lastKnownValues.put("total_transactions", totalTransactions);
        lastKnownValues.put("total_volume", totalVolume);
        
        return metrics;
    }
    
    @Override
    public void reset() {
        // Micrometer counters cannot be reset, but we can log the action
        log.info("Transaction metrics reset requested - Note: Prometheus counters are cumulative and cannot be reset");
    }
    
    public void recordTransaction(Transaction.TransactionType type, BigDecimal amount, long processingTimeMs) {
        // Increment counters
        transactionCounters.get(type).increment();
        volumeCounters.get(type).increment(amount.doubleValue());
        
        // Record total counters
        meterRegistry.counter("transactions.total").increment();
        meterRegistry.counter("transactions.volume.total").increment(amount.doubleValue());
        
        // Record processing time
        processingTimer.record(processingTimeMs, TimeUnit.MILLISECONDS);
        
        // Also record as a gauge for current window monitoring
        meterRegistry.gauge("transactions.last.processing.time", processingTimeMs);
        
        log.debug("Recorded {} transaction: amount={}, processingTime={}ms", 
                type, amount, processingTimeMs);
    }
    
    private double getPercentileValue(HistogramSnapshot snapshot, double percentile) {
        for (ValueAtPercentile vap : snapshot.percentileValues()) {
            if (Math.abs(vap.percentile() - percentile) < 0.01) {
                return vap.value(TimeUnit.MILLISECONDS);
            }
        }
        return 0.0;
    }
}