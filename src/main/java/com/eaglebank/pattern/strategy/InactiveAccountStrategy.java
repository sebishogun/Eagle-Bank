package com.eaglebank.pattern.strategy;

import com.eaglebank.entity.Account;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Strategy for INACTIVE account status.
 * Inactive accounts block all transactions and modifications except status changes.
 * Accounts become inactive after a configurable period of no activity.
 */
@Slf4j
@Component
public class InactiveAccountStrategy implements AccountStatusStrategy {
    
    @Override
    public boolean canWithdraw(Account account, BigDecimal amount) {
        log.warn("Withdrawal attempt blocked on inactive account: {}", account.getAccountNumber());
        return false;
    }
    
    @Override
    public boolean canDeposit(Account account, BigDecimal amount) {
        log.warn("Deposit attempt blocked on inactive account: {}", account.getAccountNumber());
        return false;
    }
    
    @Override
    public boolean canUpdate(Account account) {
        log.info("Update blocked on inactive account: {}", account.getAccountNumber());
        return false;
    }
    
    @Override
    public boolean canDelete(Account account) {
        // Allow deletion of inactive accounts with zero balance
        boolean canDelete = account.getBalance().compareTo(BigDecimal.ZERO) == 0;
        log.info("Inactive account {} deletion allowed: {}", account.getAccountNumber(), canDelete);
        return canDelete;
    }
    
    @Override
    public boolean canChangeStatusTo(Account account, Account.AccountStatus newStatus) {
        // Inactive accounts can only be reactivated or closed (if zero balance)
        boolean allowed = newStatus == Account.AccountStatus.ACTIVE || 
                         (newStatus == Account.AccountStatus.CLOSED && 
                          account.getBalance().compareTo(BigDecimal.ZERO) == 0);
        log.info("Inactive account {} status change to {} allowed: {}", 
                account.getAccountNumber(), newStatus, allowed);
        return allowed;
    }
    
    @Override
    public String getRestrictionReason() {
        return "Account is inactive. Please contact customer service to reactivate your account.";
    }
    
    @Override
    public Account.AccountStatus getHandledStatus() {
        return Account.AccountStatus.INACTIVE;
    }
    
    @Override
    public boolean canTransfer(Account account, BigDecimal amount) {
        log.warn("Transfer attempt blocked for inactive account {}", account.getAccountNumber());
        return false;
    }
    
    @Override
    public boolean canReceiveTransfer(Account account, BigDecimal amount) {
        log.warn("Transfer receipt blocked for inactive account {}", account.getAccountNumber());
        return false;
    }
}