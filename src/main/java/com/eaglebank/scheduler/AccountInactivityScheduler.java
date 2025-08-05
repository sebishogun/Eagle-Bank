package com.eaglebank.scheduler;

import com.eaglebank.service.AccountInactivityService;
import com.eaglebank.service.AccountInactivityService.AccountActivityStatistics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job for checking and marking inactive accounts.
 * This job runs daily to identify accounts that haven't had any
 * transactions within the configured inactivity period.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    value = "eagle-bank.scheduling.inactivity-check.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class AccountInactivityScheduler {
    
    private final AccountInactivityService accountInactivityService;
    
    /**
     * Runs daily at 2 AM to check for inactive accounts.
     * Uses ShedLock to ensure only one instance runs in a clustered environment.
     */
    @Scheduled(cron = "${eagle-bank.scheduling.inactivity-check.cron:0 0 2 * * ?}")
    @SchedulerLock(
        name = "AccountInactivityCheck",
        lockAtMostFor = "1h",
        lockAtLeastFor = "5m"
    )
    public void checkAccountInactivity() {
        log.info("Starting scheduled account inactivity check");
        
        try {
            // Get statistics before the check
            AccountActivityStatistics beforeStats = accountInactivityService.getActivityStatistics();
            log.info("Account statistics before check - Active: {}, Inactive: {}, Total: {}",
                    beforeStats.getActiveAccounts(), 
                    beforeStats.getInactiveAccounts(),
                    beforeStats.getTotalAccounts());
            
            // Run the inactivity check
            long startTime = System.currentTimeMillis();
            int accountsMarkedInactive = accountInactivityService.checkAndMarkInactiveAccounts();
            long executionTime = System.currentTimeMillis() - startTime;
            
            // Get statistics after the check
            AccountActivityStatistics afterStats = accountInactivityService.getActivityStatistics();
            
            log.info("Account inactivity check completed in {} ms. " +
                    "Marked {} accounts as inactive. " +
                    "Current stats - Active: {}, Inactive: {}, Total: {}",
                    executionTime,
                    accountsMarkedInactive,
                    afterStats.getActiveAccounts(),
                    afterStats.getInactiveAccounts(),
                    afterStats.getTotalAccounts());
                    
        } catch (Exception e) {
            log.error("Error during account inactivity check", e);
            // Don't rethrow - let the scheduler continue for next run
        }
    }
    
    /**
     * Runs monthly on the 1st at 3 AM to generate inactivity report.
     * This is for monitoring and alerting purposes.
     */
    @Scheduled(cron = "${eagle-bank.scheduling.inactivity-report.cron:0 0 3 1 * ?}")
    @SchedulerLock(
        name = "AccountInactivityReport",
        lockAtMostFor = "30m",
        lockAtLeastFor = "5m"
    )
    public void generateInactivityReport() {
        log.info("Generating account inactivity report");
        
        try {
            AccountActivityStatistics stats = accountInactivityService.getActivityStatistics();
            
            // Calculate percentages
            double inactivePercentage = stats.getTotalAccounts() > 0 
                ? (double) stats.getInactiveAccounts() / stats.getTotalAccounts() * 100 
                : 0;
                
            double activePercentage = stats.getTotalAccounts() > 0 
                ? (double) stats.getActiveAccounts() / stats.getTotalAccounts() * 100 
                : 0;
            
            log.info("Account Activity Report: " +
                    "Total Accounts: {}, " +
                    "Active: {} ({:.2f}%), " +
                    "Inactive: {} ({:.2f}%), " +
                    "Frozen: {}, " +
                    "Closed: {}, " +
                    "Inactivity Threshold: {} days",
                    stats.getTotalAccounts(),
                    stats.getActiveAccounts(), activePercentage,
                    stats.getInactiveAccounts(), inactivePercentage,
                    stats.getFrozenAccounts(),
                    stats.getClosedAccounts(),
                    stats.getInactivityThresholdDays());
                    
            // If inactive percentage is high, log a warning
            if (inactivePercentage > 20) {
                log.warn("High percentage of inactive accounts detected: {:.2f}%", inactivePercentage);
            }
            
        } catch (Exception e) {
            log.error("Error generating account inactivity report", e);
        }
    }
}