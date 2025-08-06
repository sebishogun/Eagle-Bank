package com.eaglebank.metrics;

import com.eaglebank.entity.Account;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AccountMetricsCollectorTest {
    
    private AccountMetricsCollector collector;
    private SimpleMeterRegistry meterRegistry;
    
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        collector = new AccountMetricsCollector(meterRegistry);
    }
    
    @Test
    @DisplayName("Should return correct metric name")
    void shouldReturnCorrectMetricName() {
        assertEquals("account_metrics", collector.getMetricName());
    }
    
    @Test
    @DisplayName("Should record account creation metrics")
    void shouldRecordAccountCreation() {
        // Record a savings account creation
        collector.recordAccountCreated(Account.AccountType.SAVINGS, new BigDecimal("1000.00"));
        
        Map<String, Object> metrics = collector.collect();
        Map<String, Object> sessionMetrics = (Map<String, Object>) metrics.get("session_metrics");
        
        assertNotNull(metrics);
        assertNotNull(sessionMetrics);
        assertEquals(1L, sessionMetrics.get("total_accounts"));
        assertEquals(1L, sessionMetrics.get("active_accounts"));
        assertEquals(1000L, sessionMetrics.get("total_balance"));
        assertEquals(1000.0, sessionMetrics.get("average_balance"));
        
        Map<String, Object> byType = (Map<String, Object>) sessionMetrics.get("by_type");
        Map<String, Object> savings = (Map<String, Object>) byType.get("savings");
        assertEquals(1L, savings.get("count"));
        assertEquals(1L, savings.get("new_accounts"));
    }
    
    @Test
    @DisplayName("Should record account closure metrics")
    void shouldRecordAccountClosure() {
        // First create accounts
        collector.recordAccountCreated(Account.AccountType.CHECKING, new BigDecimal("2000.00"));
        collector.recordAccountCreated(Account.AccountType.SAVINGS, new BigDecimal("3000.00"));
        
        // Then close one
        collector.recordAccountClosed(Account.AccountType.CHECKING, new BigDecimal("2000.00"));
        
        Map<String, Object> metrics = collector.collect();
        Map<String, Object> sessionMetrics = (Map<String, Object>) metrics.get("session_metrics");
        
        assertEquals(1L, sessionMetrics.get("total_accounts"));
        assertEquals(1L, sessionMetrics.get("active_accounts"));
        assertEquals(3000L, sessionMetrics.get("total_balance"));
        
        Map<String, Object> byType = (Map<String, Object>) sessionMetrics.get("by_type");
        Map<String, Object> checking = (Map<String, Object>) byType.get("checking");
        assertEquals(0L, checking.get("count"));
        assertEquals(1L, checking.get("closed_accounts"));
    }
    
    @Test
    @DisplayName("Should track balance updates")
    void shouldTrackBalanceUpdates() {
        // Create an account
        collector.recordAccountCreated(Account.AccountType.SAVINGS, new BigDecimal("1000.00"));
        
        // Update balance
        collector.recordBalanceUpdate(new BigDecimal("1000.00"), new BigDecimal("1500.00"));
        
        Map<String, Object> metrics = collector.collect();
        Map<String, Object> sessionMetrics = (Map<String, Object>) metrics.get("session_metrics");
        assertEquals(1500L, sessionMetrics.get("total_balance"));
        assertEquals(1500.0, sessionMetrics.get("average_balance"));
    }
    
    @Test
    @DisplayName("Should track account status changes")
    void shouldTrackAccountStatusChanges() {
        // Create active accounts
        collector.recordAccountCreated(Account.AccountType.SAVINGS, new BigDecimal("1000.00"));
        collector.recordAccountCreated(Account.AccountType.CHECKING, new BigDecimal("2000.00"));
        
        // Deactivate one
        collector.updateAccountStatus(true, false);
        
        Map<String, Object> metrics = collector.collect();
        Map<String, Object> sessionMetrics = (Map<String, Object>) metrics.get("session_metrics");
        // total_accounts tracks active accounts in the current implementation
        assertEquals(1L, sessionMetrics.get("total_accounts"));
        assertEquals(1L, sessionMetrics.get("active_accounts"));
    }
    
    @Test
    @DisplayName("Should calculate correct average balance")
    void shouldCalculateCorrectAverageBalance() {
        // Create multiple accounts
        collector.recordAccountCreated(Account.AccountType.SAVINGS, new BigDecimal("1000.00"));
        collector.recordAccountCreated(Account.AccountType.CHECKING, new BigDecimal("2000.00"));
        collector.recordAccountCreated(Account.AccountType.SAVINGS, new BigDecimal("3000.00"));
        
        Map<String, Object> metrics = collector.collect();
        Map<String, Object> sessionMetrics = (Map<String, Object>) metrics.get("session_metrics");
        assertEquals(2000.0, sessionMetrics.get("average_balance"));
    }
    
    @Test
    @DisplayName("Should handle zero accounts gracefully")
    void shouldHandleZeroAccounts() {
        Map<String, Object> metrics = collector.collect();
        Map<String, Object> sessionMetrics = (Map<String, Object>) metrics.get("session_metrics");
        
        assertEquals(0L, sessionMetrics.get("total_accounts"));
        assertEquals(0L, sessionMetrics.get("active_accounts"));
        assertEquals(0L, sessionMetrics.get("total_balance"));
        assertEquals(0.0, sessionMetrics.get("average_balance"));
    }
    
    @Test
    @DisplayName("Should track metrics by account type")
    void shouldTrackMetricsByAccountType() {
        // Create different account types
        collector.recordAccountCreated(Account.AccountType.SAVINGS, new BigDecimal("1000.00"));
        collector.recordAccountCreated(Account.AccountType.SAVINGS, new BigDecimal("2000.00"));
        collector.recordAccountCreated(Account.AccountType.CHECKING, new BigDecimal("3000.00"));
        
        Map<String, Object> metrics = collector.collect();
        Map<String, Object> sessionMetrics = (Map<String, Object>) metrics.get("session_metrics");
        Map<String, Object> byType = (Map<String, Object>) sessionMetrics.get("by_type");
        
        Map<String, Object> savings = (Map<String, Object>) byType.get("savings");
        assertEquals(2L, savings.get("count"));
        assertEquals(2L, savings.get("new_accounts"));
        
        Map<String, Object> checking = (Map<String, Object>) byType.get("checking");
        assertEquals(1L, checking.get("count"));
        assertEquals(1L, checking.get("new_accounts"));
    }
    
    @Test
    @DisplayName("Should verify Prometheus counters behavior on reset")
    void shouldVerifyPrometheusCountersBehavior() {
        // Create accounts
        collector.recordAccountCreated(Account.AccountType.SAVINGS, new BigDecimal("1000.00"));
        collector.recordAccountCreated(Account.AccountType.CHECKING, new BigDecimal("2000.00"));
        
        // Reset (note: Prometheus counters are cumulative and cannot be reset)
        collector.reset();
        
        Map<String, Object> metrics = collector.collect();
        Map<String, Object> sessionMetrics = (Map<String, Object>) metrics.get("session_metrics");
        
        // With Prometheus, counters remain after reset
        assertEquals(2L, sessionMetrics.get("total_accounts"));
        assertEquals(3000L, sessionMetrics.get("total_balance"));
        
        // Counters in byType also remain (Prometheus behavior)
        Map<String, Object> byType = (Map<String, Object>) sessionMetrics.get("by_type");
        Map<String, Object> savings = (Map<String, Object>) byType.get("savings");
        assertEquals(1L, savings.get("new_accounts"));
        
        // Verify Micrometer counters
        assertEquals(1.0, meterRegistry.counter("accounts.created", "type", "savings").count());
        assertEquals(1.0, meterRegistry.counter("accounts.created", "type", "checking").count());
        assertEquals(2.0, meterRegistry.counter("accounts.created.total").count());
    }
}