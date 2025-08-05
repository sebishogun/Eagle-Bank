package com.eaglebank.metrics;

import com.eaglebank.entity.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TransactionMetricsCollectorTest {
    
    private TransactionMetricsCollector collector;
    
    @BeforeEach
    void setUp() {
        collector = new TransactionMetricsCollector();
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
        assertEquals(1, metrics.get("total_transactions"));
        assertEquals(1000L, metrics.get("total_volume"));
        
        Map<String, Object> byType = (Map<String, Object>) metrics.get("by_type");
        Map<String, Object> deposits = (Map<String, Object>) byType.get("deposit");
        assertEquals(1, deposits.get("count"));
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
        assertEquals(1, withdrawals.get("count"));
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
        assertEquals(4, processingTimes.get("count"));
        assertEquals(50L, processingTimes.get("min"));
        assertEquals(200L, processingTimes.get("max"));
        assertEquals(125.0, processingTimes.get("avg"));
        assertTrue(processingTimes.containsKey("p50"));
        assertTrue(processingTimes.containsKey("p95"));
        assertTrue(processingTimes.containsKey("p99"));
    }
    
    @Test
    @DisplayName("Should track time window metrics")
    void shouldTrackTimeWindowMetrics() {
        // Record a transaction
        collector.recordTransaction(
            Transaction.TransactionType.DEPOSIT,
            new BigDecimal("1000.00"),
            100L
        );
        
        Map<String, Object> metrics = collector.collect();
        Map<String, Object> timeWindows = (Map<String, Object>) metrics.get("time_windows");
        
        assertNotNull(timeWindows);
        assertTrue(timeWindows.containsKey("one_minute"));
        assertTrue(timeWindows.containsKey("five_minutes"));
        assertTrue(timeWindows.containsKey("one_hour"));
        assertTrue(timeWindows.containsKey("twenty_four_hours"));
        
        Map<String, Object> oneMinute = (Map<String, Object>) timeWindows.get("one_minute");
        assertEquals(1, oneMinute.get("transaction_count"));
        assertTrue(oneMinute.containsKey("rate_per_second"));
        assertTrue(oneMinute.containsKey("total_volume"));
    }
    
    @Test
    @DisplayName("Should handle mixed transaction types")
    void shouldHandleMixedTransactionTypes() {
        // Record different types
        collector.recordTransaction(Transaction.TransactionType.DEPOSIT, new BigDecimal("1000"), 50L);
        collector.recordTransaction(Transaction.TransactionType.WITHDRAWAL, new BigDecimal("300"), 60L);
        collector.recordTransaction(Transaction.TransactionType.DEPOSIT, new BigDecimal("500"), 70L);
        
        Map<String, Object> metrics = collector.collect();
        
        assertEquals(3, metrics.get("total_transactions"));
        assertEquals(1800L, metrics.get("total_volume"));
        
        Map<String, Object> byType = (Map<String, Object>) metrics.get("by_type");
        Map<String, Object> deposits = (Map<String, Object>) byType.get("deposit");
        Map<String, Object> withdrawals = (Map<String, Object>) byType.get("withdrawal");
        
        assertEquals(2, deposits.get("count"));
        assertEquals(1500L, deposits.get("volume"));
        assertEquals(1, withdrawals.get("count"));
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
        assertEquals(2, beforeReset.get("total_transactions"));
        
        // Reset
        collector.reset();
        
        // Verify metrics are cleared
        Map<String, Object> afterReset = collector.collect();
        assertEquals(0, afterReset.get("total_transactions"));
        assertEquals(0L, afterReset.get("total_volume"));
        
        Map<String, Object> byType = (Map<String, Object>) afterReset.get("by_type");
        Map<String, Object> deposits = (Map<String, Object>) byType.get("deposit");
        assertEquals(0, deposits.get("count"));
    }
}