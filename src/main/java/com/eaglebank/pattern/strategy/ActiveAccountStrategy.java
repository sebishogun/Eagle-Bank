package com.eaglebank.pattern.strategy;

import com.eaglebank.entity.Account;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Strategy for ACTIVE account status.
 * Active accounts have no restrictions on operations.
 */
@Slf4j
@Component
public class ActiveAccountStrategy implements AccountStatusStrategy {
    
    @Override
    public boolean canWithdraw(Account account, BigDecimal amount) {
        log.debug("Active account {} allows withdrawals", account.getAccountNumber());
        return true;
    }
    
    @Override
    public boolean canDeposit(Account account, BigDecimal amount) {
        log.debug("Active account {} allows deposits", account.getAccountNumber());
        return true;
    }
    
    @Override
    public boolean canUpdate(Account account) {
        log.debug("Active account {} allows updates", account.getAccountNumber());
        return true;
    }
    
    @Override
    public boolean canDelete(Account account) {
        // Can only delete if balance is zero
        boolean canDelete = account.getBalance().compareTo(BigDecimal.ZERO) == 0;
        log.debug("Active account {} deletion allowed: {}", account.getAccountNumber(), canDelete);
        return canDelete;
    }
    
    @Override
    public boolean canChangeStatusTo(Account account, Account.AccountStatus newStatus) {
        // Active accounts can change to FROZEN or CLOSED
        boolean allowed = newStatus == Account.AccountStatus.FROZEN || 
                         newStatus == Account.AccountStatus.CLOSED;
        log.debug("Active account {} can change to {}: {}", 
                account.getAccountNumber(), newStatus, allowed);
        return allowed;
    }
    
    @Override
    public String getRestrictionReason() {
        return "Account must have zero balance to be deleted";
    }
    
    @Override
    public Account.AccountStatus getHandledStatus() {
        return Account.AccountStatus.ACTIVE;
    }
    
    @Override
    public boolean canTransfer(Account account, BigDecimal amount) {
        log.debug("Active account {} allows transfers", account.getAccountNumber());
        return true;
    }
    
    @Override
    public boolean canReceiveTransfer(Account account, BigDecimal amount) {
        log.debug("Active account {} allows receiving transfers", account.getAccountNumber());
        return true;
    }
}