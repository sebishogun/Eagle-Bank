package com.eaglebank.pattern.strategy;

import com.eaglebank.entity.Account;
import com.eaglebank.entity.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
public class DepositStrategy implements TransactionStrategy {
    
    private static final BigDecimal MAX_DEPOSIT_AMOUNT = new BigDecimal("1000000");
    
    @Override
    public boolean canProcess(Transaction.TransactionType type) {
        return Transaction.TransactionType.DEPOSIT == type;
    }
    
    @Override
    public void validateTransaction(Account account, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }
        
        if (amount.compareTo(MAX_DEPOSIT_AMOUNT) > 0) {
            throw new IllegalArgumentException("Deposit amount exceeds maximum limit of " + MAX_DEPOSIT_AMOUNT);
        }
        
        // Allow deposits to ACTIVE and FROZEN accounts (frozen accounts can receive deposits)
        if (account.getStatus() != Account.AccountStatus.ACTIVE && 
            account.getStatus() != Account.AccountStatus.FROZEN) {
            throw new IllegalStateException("Account must be active or frozen to accept deposits");
        }
    }
    
    @Override
    public BigDecimal calculateNewBalance(Account account, BigDecimal amount) {
        return account.getBalance().add(amount);
    }
    
    @Override
    public void postProcess(Transaction transaction) {
        log.info("Deposit of {} completed for account {}", 
                transaction.getAmount(), 
                transaction.getAccount().getAccountNumber());
        
        // Could trigger events, notifications, etc.
        if (transaction.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            log.info("Large deposit detected: {}", transaction.getAmount());
        }
    }
    
    @Override
    public String getTransactionDescription(BigDecimal amount) {
        return String.format("Deposit of %s", amount);
    }
}