package com.eaglebank.service;

import com.eaglebank.entity.Account;
import com.eaglebank.entity.Transaction;
import com.eaglebank.repository.AccountRepository;
import com.eaglebank.repository.TransactionRepository;
import com.eaglebank.pattern.observer.EventPublisher;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for detecting and marking inactive accounts based on 
 * transaction activity and configurable inactivity period.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountInactivityService {
    
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final EventPublisher eventPublisher;

    /**
     * -- GETTER --
     *  Gets the configured inactivity period in days.
     */
    @Getter
    @Value("${eagle-bank.account.inactivity-days:180}")
    private int inactivityDays;
    
    @Value("${eagle-bank.account.inactivity-check.batch-size:100}")
    private int batchSize;
    
    /**
     * Checks all active accounts for inactivity and marks them as INACTIVE
     * if they haven't had any transactions within the configured period.
     * 
     * @return Number of accounts marked as inactive
     */
    @Transactional
    public int checkAndMarkInactiveAccounts() {
        log.info("Starting account inactivity check with threshold of {} days", inactivityDays);
        
        LocalDateTime inactivityThreshold = LocalDateTime.now().minusDays(inactivityDays);
        int totalMarkedInactive = 0;
        int page = 0;
        
        // Process accounts in batches to avoid memory issues
        Page<Account> accountPage;
        do {
            accountPage = accountRepository.findByStatus(
                Account.AccountStatus.ACTIVE, 
                PageRequest.of(page++, batchSize)
            );
            
            List<Account> inactiveAccounts = accountPage.getContent().stream()
                    .filter(account -> isAccountInactive(account, inactivityThreshold))
                    .toList();
            
            for (Account account : inactiveAccounts) {
                markAccountAsInactive(account);
                totalMarkedInactive++;
            }
            
            log.debug("Processed batch {} of {}, marked {} accounts as inactive", 
                     page, accountPage.getTotalPages(), inactiveAccounts.size());
                     
        } while (accountPage.hasNext());
        
        log.info("Account inactivity check completed. Marked {} accounts as inactive", 
                totalMarkedInactive);
        
        return totalMarkedInactive;
    }
    
    /**
     * Checks if a specific account is inactive based on last transaction date.
     * 
     * @param accountId The account ID to check
     * @return true if the account is inactive, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isAccountInactive(java.util.UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
                
        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            return false; // Only check active accounts
        }
        
        LocalDateTime inactivityThreshold = LocalDateTime.now().minusDays(inactivityDays);
        return isAccountInactive(account, inactivityThreshold);
    }
    
    /**
     * Checks if an account has had any transactions after the given threshold.
     */
    private boolean isAccountInactive(Account account, LocalDateTime inactivityThreshold) {
        // Check last transaction date
        Transaction lastTransaction = transactionRepository
                .findTopByAccountIdOrderByTransactionDateDesc(account.getId())
                .orElse(null);
        
        if (lastTransaction == null) {
            // No transactions at all - check account creation date
            return account.getCreatedAt().isBefore(inactivityThreshold);
        }
        
        // Check if last transaction is before the threshold
        return lastTransaction.getTransactionDate().isBefore(inactivityThreshold);
    }
    
    /**
     * Marks an account as inactive and publishes the status change event.
     */
    private void markAccountAsInactive(Account account) {
        log.info("Marking account {} as inactive due to no activity for {} days", 
                account.getAccountNumber(), inactivityDays);
        
        account.setStatus(Account.AccountStatus.INACTIVE);
        accountRepository.save(account);
        
        String reason = String.format("Account marked inactive due to no activity for %d days", 
                                    inactivityDays);
        eventPublisher.publishAccountStatusChanged(account, reason);
    }

    /**
     * Gets statistics about account activity.
     * 
     * @return Statistics including total accounts, active, inactive counts
     */
    @Transactional(readOnly = true)
    public AccountActivityStatistics getActivityStatistics() {
        long totalAccounts = accountRepository.count();
        long activeAccounts = accountRepository.countByStatus(Account.AccountStatus.ACTIVE);
        long inactiveAccounts = accountRepository.countByStatus(Account.AccountStatus.INACTIVE);
        long frozenAccounts = accountRepository.countByStatus(Account.AccountStatus.FROZEN);
        long closedAccounts = accountRepository.countByStatus(Account.AccountStatus.CLOSED);
        
        return AccountActivityStatistics.builder()
                .totalAccounts(totalAccounts)
                .activeAccounts(activeAccounts)
                .inactiveAccounts(inactiveAccounts)
                .frozenAccounts(frozenAccounts)
                .closedAccounts(closedAccounts)
                .inactivityThresholdDays(inactivityDays)
                .build();
    }
    
    /**
     * DTO for account activity statistics.
     */
    @lombok.Data
    @lombok.Builder
    public static class AccountActivityStatistics {
        private long totalAccounts;
        private long activeAccounts;
        private long inactiveAccounts;
        private long frozenAccounts;
        private long closedAccounts;
        private int inactivityThresholdDays;
    }
}