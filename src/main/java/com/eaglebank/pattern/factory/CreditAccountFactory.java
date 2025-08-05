package com.eaglebank.pattern.factory;

import com.eaglebank.entity.Account;
import com.eaglebank.entity.User;
import com.eaglebank.util.UuidGenerator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.SecureRandom;

@Component
public class CreditAccountFactory implements AccountFactory {
    
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final BigDecimal DEFAULT_CREDIT_LIMIT = new BigDecimal("5000.00");
    
    @Override
    public String getAccountType() {
        return "CREDIT";
    }
    
    @Override
    public Account createAccount(User user, BigDecimal initialBalance) {
        // Credit accounts start with 0 balance (not initial deposit)
        // and have a credit limit instead
        Account account = Account.builder()
                .id(UuidGenerator.generateUuidV7())
                .accountNumber(generateAccountNumber())
                .accountName("Credit Account")
                .accountType(Account.AccountType.CREDIT)
                .balance(BigDecimal.ZERO) // Credit accounts start at 0
                .creditLimit(DEFAULT_CREDIT_LIMIT) // Default credit limit
                .currency("USD")
                .status(Account.AccountStatus.ACTIVE)
                .user(user)
                .build();
                
        return account;
    }
    
    private String generateAccountNumber() {
        // Generate a 16-digit credit card style number starting with "4"
        StringBuilder sb = new StringBuilder("4");
        for (int i = 0; i < 15; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
    
    @Override
    public BigDecimal getMinimumBalance() {
        // Credit accounts can have negative balance up to credit limit
        return DEFAULT_CREDIT_LIMIT.negate();
    }
    
    @Override
    public BigDecimal getMaximumBalance() {
        // Credit accounts don't have a maximum balance
        return new BigDecimal("999999999");
    }
    
    @Override
    public void validateInitialBalance(BigDecimal initialBalance) {
        // Credit accounts should start with zero balance
        if (initialBalance.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException("Credit accounts must start with zero balance");
        }
    }
}