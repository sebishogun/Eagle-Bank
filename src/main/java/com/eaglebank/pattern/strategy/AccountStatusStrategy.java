package com.eaglebank.pattern.strategy;

import com.eaglebank.entity.Account;

/**
 * Strategy interface for account status-specific business rules and validations.
 * Implementations define what operations are allowed for each account status.
 */
public interface AccountStatusStrategy {
    
    /**
     * Validates if a withdrawal is allowed for this account status
     * @param account The account to validate
     * @param amount The withdrawal amount
     * @return true if withdrawal is allowed, false otherwise
     */
    boolean canWithdraw(Account account, java.math.BigDecimal amount);
    
    /**
     * Validates if a deposit is allowed for this account status
     * @param account The account to validate
     * @param amount The deposit amount
     * @return true if deposit is allowed, false otherwise
     */
    boolean canDeposit(Account account, java.math.BigDecimal amount);
    
    /**
     * Validates if account details can be updated
     * @param account The account to validate
     * @return true if updates are allowed, false otherwise
     */
    boolean canUpdate(Account account);
    
    /**
     * Validates if account can be deleted
     * @param account The account to validate
     * @return true if deletion is allowed, false otherwise
     */
    boolean canDelete(Account account);
    
    /**
     * Validates if account status can be changed to a new status
     * @param account The account to validate
     * @param newStatus The target status
     * @return true if status change is allowed, false otherwise
     */
    boolean canChangeStatusTo(Account account, Account.AccountStatus newStatus);
    
    /**
     * Gets the reason why an operation is not allowed
     * @return Error message explaining the restriction
     */
    String getRestrictionReason();
    
    /**
     * Gets the account status this strategy handles
     * @return The account status
     */
    Account.AccountStatus getHandledStatus();
}