package com.eaglebank.pattern.strategy;

import com.eaglebank.entity.Account;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Strategy for FROZEN account status.
 * Frozen accounts allow deposits and balance inquiries but block withdrawals and most updates.
 */
@Slf4j
@Component
public class FrozenAccountStrategy implements AccountStatusStrategy {
    
    private static final String RESTRICTION_REASON = "Account is frozen. Please contact customer service.";
    
    @Override
    public boolean canWithdraw(Account account, BigDecimal amount) {
        log.warn("Withdrawal attempt blocked for frozen account {}", account.getAccountNumber());
        return false;
    }
    
    @Override
    public boolean canDeposit(Account account, BigDecimal amount) {
        // Allow deposits to frozen accounts (e.g., for debt recovery)
        log.info("Deposit allowed for frozen account {}", account.getAccountNumber());
        return true;
    }
    
    @Override
    public boolean canUpdate(Account account) {
        // Block most updates except status changes
        log.warn("Update attempt blocked for frozen account {}", account.getAccountNumber());
        return false;
    }
    
    @Override
    public boolean canDelete(Account account) {
        // Cannot delete frozen accounts
        log.warn("Delete attempt blocked for frozen account {}", account.getAccountNumber());
        return false;
    }
    
    @Override
    public boolean canChangeStatusTo(Account account, Account.AccountStatus newStatus) {
        // Frozen accounts can be reactivated or permanently closed
        boolean allowed = newStatus == Account.AccountStatus.ACTIVE || 
                         newStatus == Account.AccountStatus.CLOSED;
        log.info("Frozen account {} status change to {} allowed: {}", 
                account.getAccountNumber(), newStatus, allowed);
        return allowed;
    }
    
    @Override
    public String getRestrictionReason() {
        return RESTRICTION_REASON;
    }
    
    @Override
    public Account.AccountStatus getHandledStatus() {
        return Account.AccountStatus.FROZEN;
    }
    
    @Override
    public boolean canTransfer(Account account, BigDecimal amount) {
        log.warn("Transfer attempt blocked for frozen account {}", account.getAccountNumber());
        return false;
    }
    
    @Override
    public boolean canReceiveTransfer(Account account, BigDecimal amount) {
        // Allow receiving transfers to frozen accounts (e.g., for debt recovery)
        log.info("Transfer receipt allowed for frozen account {}", account.getAccountNumber());
        return true;
    }
}