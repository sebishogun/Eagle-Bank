package com.eaglebank.pattern.strategy;

import com.eaglebank.entity.Account;
import com.eaglebank.entity.Transaction;

import java.math.BigDecimal;

public interface TransactionStrategy {
    
    boolean canProcess(Transaction.TransactionType type);
    
    void validateTransaction(Account account, BigDecimal amount);
    
    BigDecimal calculateNewBalance(Account account, BigDecimal amount);
    
    void postProcess(Transaction transaction);
    
    String getTransactionDescription(BigDecimal amount);
}