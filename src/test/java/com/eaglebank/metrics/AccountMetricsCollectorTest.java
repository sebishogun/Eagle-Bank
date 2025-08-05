package com.eaglebank.metrics;

import com.eaglebank.entity.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AccountMetricsCollectorTest {
    
    private AccountMetricsCollector collector;
    
    @BeforeEach
    void setUp() {
        collector = new AccountMetricsCollector();
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
        
        assertNotNull(metrics);
        assertEquals(1, metrics.get("total_accounts"));
        assertEquals(1, metrics.get("active_accounts"));
        assertEquals(1000L, metrics.get("total_balance"));
        assertEquals(1000.0, metrics.get("average_balance"));
        
        Map<String, Object> byType = (Map<String, Object>) metrics.get("by_type");
        Map<String, Object> savings = (Map<String, Object>) byType.get("savings");
        assertEquals(1, savings.get("count"));
        assertEquals(1, savings.get("new_accounts"));
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
        
        assertEquals(1, metrics.get("total_accounts"));
        assertEquals(1, metrics.get("active_accounts"));
        assertEquals(3000L, metrics.get("total_balance"));
        
        Map<String, Object> byType = (Map<String, Object>) metrics.get("by_type");
        Map<String, Object> checking = (Map<String, Object>) byType.get("checking");
        assertEquals(0, checking.get("count"));
        assertEquals(1, checking.get("closed_accounts"));
    }
    
    @Test
    @DisplayName("Should track balance updates")
    void shouldTrackBalanceUpdates() {
        // Create an account
        collector.recordAccountCreated(Account.AccountType.SAVINGS, new BigDecimal("1000.00"));
        
        // Update balance
        collector.recordBalanceUpdate(new BigDecimal("1000.00"), new BigDecimal("1500.00"));
        
        Map<String, Object> metrics = collector.collect();
        assertEquals(1500L, metrics.get("total_balance"));
        assertEquals(1500.0, metrics.get("average_balance"));
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
        assertEquals(2, metrics.get("total_accounts"));
        assertEquals(1, metrics.get("active_accounts"));
    }
    
    @Test
    @DisplayName("Should calculate correct average balance")
    void shouldCalculateCorrectAverageBalance() {
        // Create multiple accounts
        collector.recordAccountCreated(Account.AccountType.SAVINGS, new BigDecimal("1000.00"));
        collector.recordAccountCreated(Account.AccountType.CHECKING, new BigDecimal("2000.00"));
        collector.recordAccountCreated(Account.AccountType.SAVINGS, new BigDecimal("3000.00"));
        
        Map<String, Object> metrics = collector.collect();
        assertEquals(2000.0, metrics.get("average_balance"));
    }
    
    @Test
    @DisplayName("Should handle zero accounts gracefully")
    void shouldHandleZeroAccounts() {
        Map<String, Object> metrics = collector.collect();
        
        assertEquals(0, metrics.get("total_accounts"));
        assertEquals(0, metrics.get("active_accounts"));
        assertEquals(0L, metrics.get("total_balance"));
        assertEquals(0.0, metrics.get("average_balance"));
    }
    
    @Test
    @DisplayName("Should track metrics by account type")
    void shouldTrackMetricsByAccountType() {
        // Create different account types
        collector.recordAccountCreated(Account.AccountType.SAVINGS, new BigDecimal("1000.00"));
        collector.recordAccountCreated(Account.AccountType.SAVINGS, new BigDecimal("2000.00"));
        collector.recordAccountCreated(Account.AccountType.CHECKING, new BigDecimal("3000.00"));
        
        Map<String, Object> metrics = collector.collect();
        Map<String, Object> byType = (Map<String, Object>) metrics.get("by_type");
        
        Map<String, Object> savings = (Map<String, Object>) byType.get("savings");
        assertEquals(2, savings.get("count"));
        assertEquals(2, savings.get("new_accounts"));
        
        Map<String, Object> checking = (Map<String, Object>) byType.get("checking");
        assertEquals(1, checking.get("count"));
        assertEquals(1, checking.get("new_accounts"));
    }
    
    @Test
    @DisplayName("Should partially reset metrics")
    void shouldPartiallyResetMetrics() {
        // Create accounts
        collector.recordAccountCreated(Account.AccountType.SAVINGS, new BigDecimal("1000.00"));
        collector.recordAccountCreated(Account.AccountType.CHECKING, new BigDecimal("2000.00"));
        
        // Reset (only new/closed accounts should reset)
        collector.reset();
        
        Map<String, Object> metrics = collector.collect();
        
        // Total counts should remain
        assertEquals(2, metrics.get("total_accounts"));
        assertEquals(3000L, metrics.get("total_balance"));
        
        // New account counts should be reset
        Map<String, Object> byType = (Map<String, Object>) metrics.get("by_type");
        Map<String, Object> savings = (Map<String, Object>) byType.get("savings");
        assertEquals(0, savings.get("new_accounts"));
    }
}