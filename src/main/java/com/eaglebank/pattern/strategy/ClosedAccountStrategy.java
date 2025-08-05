package com.eaglebank.pattern.strategy;

import com.eaglebank.entity.Account;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Strategy for CLOSED account status.
 * Closed accounts block all operations except balance inquiries.
 */
@Slf4j
@Component
public class ClosedAccountStrategy implements AccountStatusStrategy {
    
    private static final String RESTRICTION_REASON = "Account is closed. No operations are allowed.";
    
    @Override
    public boolean canWithdraw(Account account, BigDecimal amount) {
        log.error("Withdrawal attempt on closed account {}", account.getAccountNumber());
        return false;
    }
    
    @Override
    public boolean canDeposit(Account account, BigDecimal amount) {
        log.error("Deposit attempt on closed account {}", account.getAccountNumber());
        return false;
    }
    
    @Override
    public boolean canUpdate(Account account) {
        log.error("Update attempt on closed account {}", account.getAccountNumber());
        return false;
    }
    
    @Override
    public boolean canDelete(Account account) {
        // Allow deletion of closed accounts with zero balance
        boolean canDelete = account.getBalance().compareTo(BigDecimal.ZERO) == 0;
        if (!canDelete) {
            log.warn("Cannot delete closed account {} with non-zero balance", account.getAccountNumber());
        }
        return canDelete;
    }
    
    @Override
    public boolean canChangeStatusTo(Account account, Account.AccountStatus newStatus) {
        // Closed accounts cannot be reopened
        log.error("Status change attempt on closed account {}", account.getAccountNumber());
        return false;
    }
    
    @Override
    public String getRestrictionReason() {
        return RESTRICTION_REASON;
    }
    
    @Override
    public Account.AccountStatus getHandledStatus() {
        return Account.AccountStatus.CLOSED;
    }
}