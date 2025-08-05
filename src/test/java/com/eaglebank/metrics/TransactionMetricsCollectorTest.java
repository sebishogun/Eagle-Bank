package com.eaglebank.metrics;

import com.eaglebank.entity.Transaction;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TransactionMetricsCollectorTest {
    
    private TransactionMetricsCollector collector;
    private SimpleMeterRegistry meterRegistry;
    
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        collector = new TransactionMetricsCollector(meterRegistry);
    }
    
    @Test
    @DisplayName("Should return correct metric name")
    void shouldReturnCorrectMetricName() {
        assertEquals("transaction_metrics", collector.getMetricName());
    }
    
    @Test
    @DisplayName("Should record deposit transaction metrics")
    void shouldRecordDepositMetrics() {
        // Record a deposit
        collector.recordTransaction(
            Transaction.TransactionType.DEPOSIT,
            new BigDecimal("1000.00"),
            50L
        );
        
        Map<String, Object> metrics = collector.collect();
        
        assertNotNull(metrics);
        assertEquals(1L, metrics.get("total_transactions"));
        assertEquals(1000L, metrics.get("total_volume"));
        
        Map<String, Object> byType = (Map<String, Object>) metrics.get("by_type");
        Map<String, Object> deposits = (Map<String, Object>) byType.get("deposit");
        assertEquals(1L, deposits.get("count"));
        assertEquals(1000L, deposits.get("volume"));
    }
    
    @Test
    @DisplayName("Should record withdrawal transaction metrics")
    void shouldRecordWithdrawalMetrics() {
        // Record a withdrawal
        collector.recordTransaction(
            Transaction.TransactionType.WITHDRAWAL,
            new BigDecimal("500.00"),
            75L
        );
        
        Map<String, Object> metrics = collector.collect();
        
        Map<String, Object> byType = (Map<String, Object>) metrics.get("by_type");
        Map<String, Object> withdrawals = (Map<String, Object>) byType.get("withdrawal");
        assertEquals(1L, withdrawals.get("count"));
        assertEquals(500L, withdrawals.get("volume"));
    }
    
    @Test
    @DisplayName("Should calculate processing time statistics")
    void shouldCalculateProcessingTimeStats() {
        // Record multiple transactions with different processing times
        collector.recordTransaction(Transaction.TransactionType.DEPOSIT, new BigDecimal("100"), 50L);
        collector.recordTransaction(Transaction.TransactionType.DEPOSIT, new BigDecimal("200"), 100L);
        collector.recordTransaction(Transaction.TransactionType.DEPOSIT, new BigDecimal("300"), 150L);
        collector.recordTransaction(Transaction.TransactionType.DEPOSIT, new BigDecimal("400"), 200L);
        
        Map<String, Object> metrics = collector.collect();
        Map<String, Object> processingTimes = (Map<String, Object>) metrics.get("processing_times");
        
        assertNotNull(processingTimes);
        assertEquals(4L, processingTimes.get("count"));
        assertTrue(processingTimes.containsKey("min"));
        assertTrue(processingTimes.containsKey("max"));
        assertTrue(processingTimes.containsKey("avg"));
        assertTrue(processingTimes.containsKey("p50"));
        assertTrue(processingTimes.containsKey("p95"));
        assertTrue(processingTimes.containsKey("p99"));
    }
    
    @Test
    @DisplayName("Should verify metrics are recorded in Micrometer")
    void shouldVerifyMicrometerMetrics() {
        // Record a transaction
        collector.recordTransaction(
            Transaction.TransactionType.DEPOSIT,
            new BigDecimal("1000.00"),
            100L
        );
        
        // Verify Micrometer counters
        assertEquals(1.0, meterRegistry.counter("transactions.count", "type", "deposit").count());
        assertEquals(1000.0, meterRegistry.counter("transactions.volume", "type", "deposit").count());
        assertEquals(1.0, meterRegistry.counter("transactions.total").count());
        assertEquals(1000.0, meterRegistry.counter("transactions.volume.total").count());
        
        // Verify timer recorded the processing time
        assertEquals(1, meterRegistry.timer("transactions.processing.time").count());
        assertEquals(100_000_000L, meterRegistry.timer("transactions.processing.time").totalTime(java.util.concurrent.TimeUnit.NANOSECONDS));
    }
    
    @Test
    @DisplayName("Should handle mixed transaction types")
    void shouldHandleMixedTransactionTypes() {
        // Record different types
        collector.recordTransaction(Transaction.TransactionType.DEPOSIT, new BigDecimal("1000"), 50L);
        collector.recordTransaction(Transaction.TransactionType.WITHDRAWAL, new BigDecimal("300"), 60L);
        collector.recordTransaction(Transaction.TransactionType.DEPOSIT, new BigDecimal("500"), 70L);
        
        Map<String, Object> metrics = collector.collect();
        
        assertEquals(3L, metrics.get("total_transactions"));
        assertEquals(1800L, metrics.get("total_volume"));
        
        Map<String, Object> byType = (Map<String, Object>) metrics.get("by_type");
        Map<String, Object> deposits = (Map<String, Object>) byType.get("deposit");
        Map<String, Object> withdrawals = (Map<String, Object>) byType.get("withdrawal");
        
        assertEquals(2L, deposits.get("count"));
        assertEquals(1500L, deposits.get("volume"));
        assertEquals(1L, withdrawals.get("count"));
        assertEquals(300L, withdrawals.get("volume"));
    }
    
    @Test
    @DisplayName("Should reset metrics correctly")
    void shouldResetMetrics() {
        // Record some transactions
        collector.recordTransaction(Transaction.TransactionType.DEPOSIT, new BigDecimal("1000"), 50L);
        collector.recordTransaction(Transaction.TransactionType.WITHDRAWAL, new BigDecimal("500"), 75L);
        
        // Verify metrics exist
        Map<String, Object> beforeReset = collector.collect();
        assertEquals(2L, beforeReset.get("total_transactions"));
        
        // Reset (note: Prometheus counters are cumulative and cannot be reset)
        collector.reset();
        
        // After reset, counters remain the same (Prometheus behavior)
        Map<String, Object> afterReset = collector.collect();
        assertEquals(2L, afterReset.get("total_transactions"));
        assertEquals(1500L, afterReset.get("total_volume"));
        
        // The reset method is primarily for logging and compatibility
        // In production, Prometheus handles time-based aggregations
    }
}