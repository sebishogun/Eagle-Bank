package com.eaglebank.metrics;

import com.eaglebank.entity.Account;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class AccountMetricsCollector implements MetricsCollector {
    
    private final Map<Account.AccountType, AtomicInteger> accountCounts = new ConcurrentHashMap<>();
    private final Map<Account.AccountType, AtomicInteger> newAccounts = new ConcurrentHashMap<>();
    private final Map<Account.AccountType, AtomicInteger> closedAccounts = new ConcurrentHashMap<>();
    private final AtomicLong totalBalance = new AtomicLong(0);
    private final AtomicInteger totalAccounts = new AtomicInteger(0);
    private final AtomicInteger activeAccounts = new AtomicInteger(0);
    private LocalDateTime lastResetTime = LocalDateTime.now();
    
    public AccountMetricsCollector() {
        // Initialize counters
        for (Account.AccountType type : Account.AccountType.values()) {
            accountCounts.put(type, new AtomicInteger(0));
            newAccounts.put(type, new AtomicInteger(0));
            closedAccounts.put(type, new AtomicInteger(0));
        }
    }
    
    @Override
    public String getMetricName() {
        return "account_metrics";
    }
    
    @Override
    public Map<String, Object> collect() {
        Map<String, Object> metrics = new HashMap<>();
        
        // Basic metrics
        metrics.put("total_accounts", totalAccounts.get());
        metrics.put("active_accounts", activeAccounts.get());
        metrics.put("total_balance", totalBalance.get());
        metrics.put("average_balance", calculateAverageBalance());
        
        // Account type breakdown
        Map<String, Object> byType = new HashMap<>();
        for (Account.AccountType type : Account.AccountType.values()) {
            Map<String, Object> typeMetrics = new HashMap<>();
            typeMetrics.put("count", accountCounts.get(type).get());
            typeMetrics.put("new_accounts", newAccounts.get(type).get());
            typeMetrics.put("closed_accounts", closedAccounts.get(type).get());
            byType.put(type.name().toLowerCase(), typeMetrics);
        }
        metrics.put("by_type", byType);
        
        // Time-based metrics
        Map<String, Object> timeMetrics = new HashMap<>();
        timeMetrics.put("last_reset", lastResetTime.toString());
        timeMetrics.put("collection_period_hours", 
            java.time.Duration.between(lastResetTime, LocalDateTime.now()).toHours());
        metrics.put("time_info", timeMetrics);
        
        return metrics;
    }
    
    @Override
    public void reset() {
        newAccounts.values().forEach(counter -> counter.set(0));
        closedAccounts.values().forEach(counter -> counter.set(0));
        lastResetTime = LocalDateTime.now();
        log.info("Account metrics partially reset (new/closed accounts)");
    }
    
    public void recordAccountCreated(Account.AccountType type, BigDecimal initialBalance) {
        accountCounts.get(type).incrementAndGet();
        newAccounts.get(type).incrementAndGet();
        totalAccounts.incrementAndGet();
        activeAccounts.incrementAndGet();
        totalBalance.addAndGet(initialBalance.longValue());
        
        log.debug("Recorded new {} account with balance {}", type, initialBalance);
    }
    
    public void recordAccountClosed(Account.AccountType type, BigDecimal finalBalance) {
        accountCounts.get(type).decrementAndGet();
        closedAccounts.get(type).incrementAndGet();
        totalAccounts.decrementAndGet();
        activeAccounts.decrementAndGet();
        totalBalance.addAndGet(-finalBalance.longValue());
        
        log.debug("Recorded closed {} account with balance {}", type, finalBalance);
    }
    
    public void recordBalanceUpdate(BigDecimal oldBalance, BigDecimal newBalance) {
        long difference = newBalance.longValue() - oldBalance.longValue();
        totalBalance.addAndGet(difference);
    }
    
    public void updateAccountStatus(boolean wasActive, boolean isActive) {
        if (wasActive && !isActive) {
            activeAccounts.decrementAndGet();
        } else if (!wasActive && isActive) {
            activeAccounts.incrementAndGet();
        }
    }
    
    private double calculateAverageBalance() {
        int total = totalAccounts.get();
        if (total == 0) return 0.0;
        return (double) totalBalance.get() / total;
    }
}