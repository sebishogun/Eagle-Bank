package com.eaglebank.validation;

import com.eaglebank.entity.Account;
import com.eaglebank.exception.InvalidStateTransitionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Validates account status transitions based on business rules.
 * Implements a state machine for account status transitions.
 */
@Slf4j
@Component
public class AccountStatusTransitionValidator {
    
    // Define valid transitions from each status
    private static final Map<Account.AccountStatus, Set<Account.AccountStatus>> VALID_TRANSITIONS = new HashMap<>();
    
    static {
        // ACTIVE accounts can be frozen, closed, or become inactive
        VALID_TRANSITIONS.put(Account.AccountStatus.ACTIVE, 
            Set.of(Account.AccountStatus.FROZEN, Account.AccountStatus.CLOSED, Account.AccountStatus.INACTIVE));
        
        // FROZEN accounts can be reactivated or closed
        VALID_TRANSITIONS.put(Account.AccountStatus.FROZEN, 
            Set.of(Account.AccountStatus.ACTIVE, Account.AccountStatus.CLOSED));
        
        // INACTIVE accounts can be reactivated or closed (if zero balance)
        VALID_TRANSITIONS.put(Account.AccountStatus.INACTIVE,
            Set.of(Account.AccountStatus.ACTIVE, Account.AccountStatus.CLOSED));
        
        // CLOSED accounts cannot change status
        VALID_TRANSITIONS.put(Account.AccountStatus.CLOSED, Set.of());
    }
    
    /**
     * Validates if a status transition is allowed
     * @param account The account to validate
     * @param newStatus The target status
     * @param reason The reason for status change
     * @throws InvalidStateTransitionException if transition is not allowed
     */
    public void validateTransition(Account account, Account.AccountStatus newStatus, String reason) {
        Account.AccountStatus currentStatus = account.getStatus();
        
        // No change needed
        if (currentStatus == newStatus) {
            log.debug("Account {} already has status {}", account.getAccountNumber(), newStatus);
            return;
        }
        
        // Check if transition is valid
        Set<Account.AccountStatus> allowedTransitions = VALID_TRANSITIONS.get(currentStatus);
        if (allowedTransitions == null || !allowedTransitions.contains(newStatus)) {
            String message = String.format("Invalid status transition from %s to %s for account %s",
                currentStatus, newStatus, account.getAccountNumber());
            log.error(message);
            throw new InvalidStateTransitionException(message);
        }
        
        // Additional business rules
        validateBusinessRules(account, currentStatus, newStatus, reason);
        
        log.info("Status transition validated for account {} from {} to {}", 
            account.getAccountNumber(), currentStatus, newStatus);
    }
    
    /**
     * Validates business rules for specific transitions
     */
    private void validateBusinessRules(Account account, Account.AccountStatus currentStatus, 
                                     Account.AccountStatus newStatus, String reason) {
        
        // Closing an account requires zero balance
        if (newStatus == Account.AccountStatus.CLOSED && 
            account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new InvalidStateTransitionException(
                "Cannot close account with non-zero balance. Current balance: " + account.getBalance());
        }
        
        // Freezing, closing, or marking inactive requires a reason
        if ((newStatus == Account.AccountStatus.FROZEN || 
             newStatus == Account.AccountStatus.CLOSED ||
             newStatus == Account.AccountStatus.INACTIVE) 
            && (reason == null || reason.trim().isEmpty())) {
            throw new InvalidStateTransitionException(
                "A reason must be provided for freezing, closing, or marking an account inactive");
        }
        
        // Special validation for INACTIVE to CLOSED transition
        if (currentStatus == Account.AccountStatus.INACTIVE && 
            newStatus == Account.AccountStatus.CLOSED &&
            account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new InvalidStateTransitionException(
                "Cannot close inactive account with non-zero balance");
        }
        
        // Reactivating a frozen account may require additional checks
        if (currentStatus == Account.AccountStatus.FROZEN && 
            newStatus == Account.AccountStatus.ACTIVE) {
            log.info("Reactivating frozen account {} with reason: {}", 
                account.getAccountNumber(), reason);
            // In a real system, this might require manager approval
        }
    }
    
    /**
     * Gets allowed transitions for a given status
     * @param status The current status
     * @return Set of allowed target statuses
     */
    public Set<Account.AccountStatus> getAllowedTransitions(Account.AccountStatus status) {
        return VALID_TRANSITIONS.getOrDefault(status, Set.of());
    }
    
    /**
     * Checks if a specific transition is allowed
     * @param from Current status
     * @param to Target status
     * @return true if transition is allowed
     */
    public boolean isTransitionAllowed(Account.AccountStatus from, Account.AccountStatus to) {
        if (from == to) return true;
        Set<Account.AccountStatus> allowed = VALID_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }
}