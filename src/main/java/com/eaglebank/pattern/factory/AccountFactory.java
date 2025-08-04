package com.eaglebank.pattern.factory;

import com.eaglebank.entity.Account;
import com.eaglebank.entity.User;

import java.math.BigDecimal;

public interface AccountFactory {
    
    Account createAccount(User user, BigDecimal initialBalance);
    
    String getAccountType();
    
    BigDecimal getMinimumBalance();
    
    BigDecimal getMaximumBalance();
    
    void validateInitialBalance(BigDecimal initialBalance);
}