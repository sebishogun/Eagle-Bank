package com.eaglebank.pattern.factory;

import com.eaglebank.entity.Account;
import com.eaglebank.entity.User;
import com.eaglebank.util.UuidGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.SecureRandom;

@Slf4j
@Component
@RequiredArgsConstructor
public class SavingsAccountFactory implements AccountFactory {
    
    private static final Account.AccountType ACCOUNT_TYPE = Account.AccountType.SAVINGS;
    private static final BigDecimal MIN_BALANCE = new BigDecimal("100");
    private static final BigDecimal MAX_BALANCE = new BigDecimal("10000000");
    private static final SecureRandom RANDOM = new SecureRandom();
    
    @Override
    public Account createAccount(User user, BigDecimal initialBalance) {
        validateInitialBalance(initialBalance);
        
        Account account = Account.builder()
                .id(UuidGenerator.generateUuidV7())
                .accountNumber(generateAccountNumber())
                .accountName(user.getFirstName() + " " + user.getLastName() + " Savings")
                .accountType(ACCOUNT_TYPE)
                .balance(initialBalance)
                .currency("USD")
                .status(Account.AccountStatus.ACTIVE)
                .user(user)
                .build();
        
        log.info("Created savings account with number: {}", account.getAccountNumber());
        return account;
    }
    
    @Override
    public String getAccountType() {
        return ACCOUNT_TYPE.name();
    }
    
    @Override
    public BigDecimal getMinimumBalance() {
        return MIN_BALANCE;
    }
    
    @Override
    public BigDecimal getMaximumBalance() {
        return MAX_BALANCE;
    }
    
    @Override
    public void validateInitialBalance(BigDecimal initialBalance) {
        if (initialBalance.compareTo(MIN_BALANCE) < 0) {
            throw new IllegalArgumentException(
                String.format("Initial balance must be at least %s for savings account", MIN_BALANCE)
            );
        }
        
        if (initialBalance.compareTo(MAX_BALANCE) > 0) {
            throw new IllegalArgumentException(
                String.format("Initial balance cannot exceed %s for savings account", MAX_BALANCE)
            );
        }
    }
    
    private String generateAccountNumber() {
        // SAV prefix for savings accounts
        StringBuilder sb = new StringBuilder("SAV");
        for (int i = 0; i < 10; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}