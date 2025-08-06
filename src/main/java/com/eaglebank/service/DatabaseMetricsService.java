package com.eaglebank.service;

import com.eaglebank.entity.Account;
import com.eaglebank.repository.AccountRepository;
import com.eaglebank.repository.TransactionRepository;
import com.eaglebank.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service that queries the database for actual metrics
 * to supplement the event-based metrics collected by MetricsCollector.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseMetricsService {
    
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    
    // Cache for database metrics
    private final Map<String, Object> cachedMetrics = new ConcurrentHashMap<>();
    
    /**
     * Updates database metrics every minute
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 5000)
    @Transactional(readOnly = true)
    public void updateDatabaseMetrics() {
        log.debug("Updating database metrics");
        
        try {
            // Total users
            long totalUsers = userRepository.count();
            cachedMetrics.put("total_users", totalUsers);
            
            // Account metrics
            Map<String, Object> accountMetrics = new HashMap<>();
            long totalAccounts = 0;
            long totalActiveAccounts = 0;
            BigDecimal totalBalance = BigDecimal.ZERO;
            
            for (Account.AccountType type : Account.AccountType.values()) {
                long countByType = accountRepository.countByAccountType(type);
                long activeByType = accountRepository.countByAccountTypeAndStatus(type, Account.AccountStatus.ACTIVE);
                BigDecimal balanceByType = accountRepository.sumBalanceByAccountTypeAndStatus(type, Account.AccountStatus.ACTIVE);
                
                totalAccounts += countByType;
                totalActiveAccounts += activeByType;
                if (balanceByType != null) {
                    totalBalance = totalBalance.add(balanceByType);
                }
                
                Map<String, Object> typeMetrics = new HashMap<>();
                typeMetrics.put("total", countByType);
                typeMetrics.put("active", activeByType);
                typeMetrics.put("balance", balanceByType != null ? balanceByType : BigDecimal.ZERO);
                accountMetrics.put(type.name().toLowerCase(), typeMetrics);
            }
            
            cachedMetrics.put("accounts_by_type", accountMetrics);
            cachedMetrics.put("total_accounts", totalAccounts);
            cachedMetrics.put("total_active_accounts", totalActiveAccounts);
            cachedMetrics.put("total_balance", totalBalance);
            
            // Calculate average balance
            BigDecimal averageBalance = totalActiveAccounts > 0 
                ? totalBalance.divide(BigDecimal.valueOf(totalActiveAccounts), 2, BigDecimal.ROUND_HALF_UP)
                : BigDecimal.ZERO;
            cachedMetrics.put("average_balance", averageBalance);
            
            // Transaction metrics
            long totalTransactions = transactionRepository.count();
            cachedMetrics.put("total_transactions", totalTransactions);
            
            // Account status distribution
            Map<String, Long> statusDistribution = new HashMap<>();
            for (Account.AccountStatus status : Account.AccountStatus.values()) {
                long count = accountRepository.countByStatus(status);
                statusDistribution.put(status.name().toLowerCase(), count);
            }
            cachedMetrics.put("accounts_by_status", statusDistribution);
            
            log.info("Database metrics updated successfully");
            
        } catch (Exception e) {
            log.error("Error updating database metrics", e);
        }
    }
    
    /**
     * Get the latest database metrics
     * @return Map of metric name to value
     */
    public Map<String, Object> getDatabaseMetrics() {
        // If cache is empty, update immediately
        if (cachedMetrics.isEmpty()) {
            updateDatabaseMetrics();
        }
        return new HashMap<>(cachedMetrics);
    }
    
    /**
     * Get a specific database metric
     * @param metricName the name of the metric
     * @return the metric value, or null if not found
     */
    public Object getDatabaseMetric(String metricName) {
        return cachedMetrics.get(metricName);
    }
    
    /**
     * Force refresh of database metrics
     */
    public void refreshMetrics() {
        updateDatabaseMetrics();
    }
}