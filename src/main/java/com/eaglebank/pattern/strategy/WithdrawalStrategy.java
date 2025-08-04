package com.eaglebank.pattern.strategy;

import com.eaglebank.entity.Account;
import com.eaglebank.entity.Transaction;
import com.eaglebank.exception.InsufficientFundsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
public class WithdrawalStrategy implements TransactionStrategy {
    
    private static final BigDecimal MIN_BALANCE = BigDecimal.ZERO;
    private static final BigDecimal MAX_WITHDRAWAL_AMOUNT = new BigDecimal("50000");
    
    @Override
    public boolean canProcess(Transaction.TransactionType type) {
        return Transaction.TransactionType.WITHDRAWAL == type;
    }
    
    @Override
    public void validateTransaction(Account account, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }
        
        if (amount.compareTo(MAX_WITHDRAWAL_AMOUNT) > 0) {
            throw new IllegalArgumentException("Withdrawal amount exceeds maximum limit of " + MAX_WITHDRAWAL_AMOUNT);
        }
        
        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account must be active for withdrawals");
        }
        
        BigDecimal balanceAfterWithdrawal = account.getBalance().subtract(amount);
        if (balanceAfterWithdrawal.compareTo(MIN_BALANCE) < 0) {
            throw new InsufficientFundsException(
                String.format("Insufficient funds. Available balance: %s, Requested amount: %s",
                    account.getBalance(), amount)
            );
        }
    }
    
    @Override
    public BigDecimal calculateNewBalance(Account account, BigDecimal amount) {
        return account.getBalance().subtract(amount);
    }
    
    @Override
    public void postProcess(Transaction transaction) {
        log.info("Withdrawal of {} completed for account {}", 
                transaction.getAmount(), 
                transaction.getAccount().getAccountNumber());
        
        // Check for low balance warning
        if (transaction.getBalanceAfter().compareTo(new BigDecimal("100")) < 0) {
            log.warn("Low balance warning for account {}: {}", 
                    transaction.getAccount().getAccountNumber(), 
                    transaction.getBalanceAfter());
        }
    }
    
    @Override
    public String getTransactionDescription(BigDecimal amount) {
        return String.format("Withdrawal of %s", amount);
    }
}