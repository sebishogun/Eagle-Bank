package com.eaglebank.pattern.strategy;

import com.eaglebank.entity.Account;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory for retrieving the appropriate AccountStatusStrategy based on account status.
 */
@Slf4j
@Component
public class AccountStatusStrategyFactory {
    
    private final Map<Account.AccountStatus, AccountStatusStrategy> strategies = new HashMap<>();
    
    // Constructor injection of all strategies
    public AccountStatusStrategyFactory(List<AccountStatusStrategy> strategyList) {
        strategyList.forEach(strategy -> 
            strategies.put(strategy.getHandledStatus(), strategy)
        );
        log.info("Initialized AccountStatusStrategyFactory with {} strategies", strategies.size());
    }
    
    /**
     * Get the strategy for a specific account status
     * @param status The account status
     * @return The appropriate strategy
     * @throws IllegalArgumentException if no strategy found for the status
     */
    public AccountStatusStrategy getStrategy(Account.AccountStatus status) {
        AccountStatusStrategy strategy = strategies.get(status);
        if (strategy == null) {
            log.error("No strategy found for account status: {}", status);
            throw new IllegalArgumentException("No strategy found for account status: " + status);
        }
        return strategy;
    }
    
    /**
     * Get the strategy for an account
     * @param account The account
     * @return The appropriate strategy based on account status
     */
    public AccountStatusStrategy getStrategy(Account account) {
        return getStrategy(account.getStatus());
    }
}