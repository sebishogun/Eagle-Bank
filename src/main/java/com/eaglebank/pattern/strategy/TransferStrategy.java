package com.eaglebank.pattern.strategy;

import com.eaglebank.entity.Account;
import com.eaglebank.entity.Transaction;
import com.eaglebank.exception.InsufficientFundsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
public class TransferStrategy implements TransactionStrategy {
    
    @Override
    public boolean canProcess(Transaction.TransactionType type) {
        return Transaction.TransactionType.TRANSFER == type;
    }
    
    @Override
    public void validateTransaction(Account account, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }
        
        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new IllegalStateException("Source account must be active for transfers");
        }
        
        BigDecimal balanceAfterTransfer = account.getBalance().subtract(amount);
        if (balanceAfterTransfer.compareTo(BigDecimal.ZERO) < 0) {
            throw new InsufficientFundsException(
                String.format("Insufficient funds for transfer. Available balance: %s, Requested amount: %s",
                    account.getBalance(), amount)
            );
        }
    }
    
    @Override
    public BigDecimal calculateNewBalance(Account account, BigDecimal amount) {
        // For source account (withdrawal part of transfer)
        return account.getBalance().subtract(amount);
    }
    
    @Override
    public void postProcess(Transaction transaction) {
        log.info("Transfer of {} processed for transaction {}", 
                transaction.getAmount(), 
                transaction.getReferenceNumber());
    }
    
    @Override
    public String getTransactionDescription(BigDecimal amount) {
        return String.format("Transfer of %s", amount);
    }
}