package com.eaglebank.metrics;

import com.eaglebank.entity.Account;
import com.eaglebank.service.DatabaseMetricsService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class AccountMetricsCollector implements MetricsCollector {
    
    private final MeterRegistry meterRegistry;
    private final Map<Account.AccountType, Counter> accountCreatedCounters = new ConcurrentHashMap<>();
    private final Map<Account.AccountType, Counter> accountClosedCounters = new ConcurrentHashMap<>();
    private final Map<Account.AccountType, AtomicLong> activeAccountGauges = new ConcurrentHashMap<>();
    private final AtomicLong totalBalance = new AtomicLong(0);
    private final AtomicLong totalActiveAccounts = new AtomicLong(0);
    private LocalDateTime lastResetTime = LocalDateTime.now();
    
    @Autowired
    @Lazy
    private DatabaseMetricsService databaseMetricsService;
    
    public AccountMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Initialize metrics for each account type
        for (Account.AccountType type : Account.AccountType.values()) {
            String typeName = type.name().toLowerCase();
            
            accountCreatedCounters.put(type, Counter.builder("accounts.created")
                    .tag("type", typeName)
                    .description("Number of accounts created by type")
                    .register(meterRegistry));
            
            accountClosedCounters.put(type, Counter.builder("accounts.closed")
                    .tag("type", typeName)
                    .description("Number of accounts closed by type")
                    .register(meterRegistry));
            
            AtomicLong activeGauge = new AtomicLong(0);
            activeAccountGauges.put(type, activeGauge);
            Gauge.builder("accounts.active", activeGauge, AtomicLong::get)
                    .tag("type", typeName)
                    .description("Number of active accounts by type")
                    .register(meterRegistry);
        }
        
        // Register total gauges
        Gauge.builder("accounts.balance.total", totalBalance, AtomicLong::get)
                .description("Total balance across all accounts")
                .baseUnit("currency")
                .register(meterRegistry);
        
        Gauge.builder("accounts.active.total", totalActiveAccounts, AtomicLong::get)
                .description("Total number of active accounts")
                .register(meterRegistry);
        
        // Average balance gauge
        Gauge.builder("accounts.balance.average", this, AccountMetricsCollector::calculateAverageBalance)
                .description("Average account balance")
                .baseUnit("currency")
                .register(meterRegistry);
        
        // Database metrics gauges
        Gauge.builder("accounts.database.total_active", this, collector -> {
                    if (collector.databaseMetricsService != null) {
                        Object value = collector.databaseMetricsService.getDatabaseMetric("total_active_accounts");
                        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
                    }
                    return 0.0;
                })
                .description("Total active accounts in database")
                .register(meterRegistry);
        
        Gauge.builder("accounts.database.total_balance", this, collector -> {
                    if (collector.databaseMetricsService != null) {
                        Object value = collector.databaseMetricsService.getDatabaseMetric("total_balance");
                        if (value instanceof BigDecimal) {
                            return ((BigDecimal) value).doubleValue();
                        }
                        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
                    }
                    return 0.0;
                })
                .description("Total balance across all accounts in database")
                .baseUnit("currency")
                .register(meterRegistry);
        
        Gauge.builder("accounts.database.total", this, collector -> {
                    if (collector.databaseMetricsService != null) {
                        Object value = collector.databaseMetricsService.getDatabaseMetric("total_accounts");
                        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
                    }
                    return 0.0;
                })
                .description("Total accounts in database")
                .register(meterRegistry);
    }
    
    @Override
    public String getMetricName() {
        return "account_metrics";
    }
    
    @Override
    public Map<String, Object> collect() {
        Map<String, Object> metrics = new HashMap<>();
        
        // Session metrics (since startup)
        Map<String, Object> sessionMetrics = new HashMap<>();
        long totalCreated = 0;
        long totalClosed = 0;
        long totalActive = totalActiveAccounts.get();
        
        Map<String, Object> byType = new HashMap<>();
        for (Account.AccountType type : Account.AccountType.values()) {
            String typeName = type.name().toLowerCase();
            long created = (long) accountCreatedCounters.get(type).count();
            long closed = (long) accountClosedCounters.get(type).count();
            long active = activeAccountGauges.get(type).get();
            
            totalCreated += created;
            totalClosed += closed;
            
            Map<String, Object> typeMetrics = new HashMap<>();
            typeMetrics.put("count", active);
            typeMetrics.put("new_accounts", created);
            typeMetrics.put("closed_accounts", closed);
            byType.put(typeName, typeMetrics);
        }
        
        sessionMetrics.put("total_accounts", totalActive);
        sessionMetrics.put("active_accounts", totalActive);
        sessionMetrics.put("total_balance", totalBalance.get());
        sessionMetrics.put("average_balance", calculateAverageBalance());
        sessionMetrics.put("by_type", byType);
        sessionMetrics.put("accounts_created_session", totalCreated);
        sessionMetrics.put("accounts_closed_session", totalClosed);
        
        metrics.put("session_metrics", sessionMetrics);
        
        // Database metrics (actual DB state)
        if (databaseMetricsService != null) {
            Map<String, Object> dbMetrics = databaseMetricsService.getDatabaseMetrics();
            metrics.put("database_metrics", dbMetrics);
            
            // Also add key DB metrics at top level for Prometheus
            if (dbMetrics.containsKey("total_active_accounts")) {
                metrics.put("db_total_active_accounts", dbMetrics.get("total_active_accounts"));
            }
            if (dbMetrics.containsKey("total_balance")) {
                metrics.put("db_total_balance", dbMetrics.get("total_balance"));
            }
        }
        
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
        // Note: Prometheus counters are cumulative and cannot be reset
        lastResetTime = LocalDateTime.now();
        log.info("Account metrics reset timestamp updated - Note: Prometheus counters are cumulative");
    }
    
    public void recordAccountCreated(Account.AccountType type, BigDecimal initialBalance) {
        accountCreatedCounters.get(type).increment();
        activeAccountGauges.get(type).incrementAndGet();
        totalActiveAccounts.incrementAndGet();
        totalBalance.addAndGet(initialBalance.longValue());
        
        // Also record total counter
        meterRegistry.counter("accounts.created.total").increment();
        
        log.debug("Recorded new {} account with balance {}", type, initialBalance);
    }
    
    public void recordAccountClosed(Account.AccountType type, BigDecimal finalBalance) {
        accountClosedCounters.get(type).increment();
        activeAccountGauges.get(type).decrementAndGet();
        totalActiveAccounts.decrementAndGet();
        totalBalance.addAndGet(-finalBalance.longValue());
        
        // Also record total counter
        meterRegistry.counter("accounts.closed.total").increment();
        
        log.debug("Recorded closed {} account with balance {}", type, finalBalance);
    }
    
    public void recordBalanceUpdate(BigDecimal oldBalance, BigDecimal newBalance) {
        long difference = newBalance.longValue() - oldBalance.longValue();
        totalBalance.addAndGet(difference);
    }
    
    public void updateAccountStatus(boolean wasActive, boolean isActive) {
        if (wasActive && !isActive) {
            totalActiveAccounts.decrementAndGet();
        } else if (!wasActive && isActive) {
            totalActiveAccounts.incrementAndGet();
        }
    }
    
    private double calculateAverageBalance() {
        long total = totalActiveAccounts.get();
        if (total == 0) return 0.0;
        return (double) totalBalance.get() / total;
    }
}