package com.eaglebank.pattern.strategy;

import com.eaglebank.entity.Account;
import com.eaglebank.entity.Transaction;
import com.eaglebank.exception.InsufficientFundsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
public class CreditWithdrawalStrategy implements TransactionStrategy {
    
    @Override
    public boolean canProcess(Transaction.TransactionType type) {
        // Handle withdrawals for credit accounts
        return type == Transaction.TransactionType.WITHDRAWAL;
    }
    
    @Override
    public void validateTransaction(Account account, BigDecimal amount) {
        if (account.getAccountType() != Account.AccountType.CREDIT) {
            throw new IllegalArgumentException("This strategy is only for credit accounts");
        }
        
        // For credit accounts, check if withdrawal would exceed credit limit
        BigDecimal creditLimit = account.getCreditLimit() != null ? account.getCreditLimit() : BigDecimal.ZERO;
        BigDecimal currentBalance = account.getBalance();
        BigDecimal newBalance = currentBalance.subtract(amount);
        
        // Credit accounts can go negative up to the credit limit
        if (newBalance.abs().compareTo(creditLimit) > 0) {
            BigDecimal availableCredit = creditLimit.add(currentBalance);
            throw new InsufficientFundsException(
                String.format("Insufficient credit. Available credit: %.2f, Requested: %.2f", 
                    availableCredit, amount)
            );
        }
    }
    
    @Override
    public BigDecimal calculateNewBalance(Account account, BigDecimal amount) {
        return account.getBalance().subtract(amount);
    }
    
    @Override
    public String getTransactionDescription(BigDecimal amount) {
        return String.format("Credit withdrawal of %.2f", amount);
    }
    
    @Override
    public void postProcess(Transaction transaction) {
        log.info("Credit withdrawal completed. New balance: {}, Credit used: {}", 
            transaction.getBalanceAfter(),
            transaction.getBalanceAfter().abs());
    }
}