package com.eaglebank.pattern;

import com.eaglebank.entity.Account;
import com.eaglebank.entity.Transaction;
import com.eaglebank.entity.User;
import com.eaglebank.pattern.strategy.DepositStrategy;
import com.eaglebank.pattern.strategy.TransactionStrategy;
import com.eaglebank.pattern.strategy.TransactionStrategyFactory;
import com.eaglebank.pattern.strategy.WithdrawalStrategy;
import com.eaglebank.util.UuidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StrategyPatternTest {
    
    private TransactionStrategyFactory strategyFactory;
    private Account testAccount;
    
    @BeforeEach
    void setUp() {
        DepositStrategy depositStrategy = new DepositStrategy();
        WithdrawalStrategy withdrawalStrategy = new WithdrawalStrategy();
        
        strategyFactory = new TransactionStrategyFactory(List.of(depositStrategy, withdrawalStrategy));
        
        User user = User.builder()
                .id(UuidGenerator.generateUuidV7())
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .build();
        
        testAccount = Account.builder()
                .id(UuidGenerator.generateUuidV7())
                .accountNumber("TEST123456")
                .accountType(Account.AccountType.SAVINGS)
                .balance(new BigDecimal("1000"))
                .status(Account.AccountStatus.ACTIVE)
                .user(user)
                .build();
    }
    
    @Test
    void testDepositStrategy() {
        TransactionStrategy strategy = strategyFactory.getStrategy(Transaction.TransactionType.DEPOSIT);
        
        assertTrue(strategy.canProcess(Transaction.TransactionType.DEPOSIT));
        assertFalse(strategy.canProcess(Transaction.TransactionType.WITHDRAWAL));
        
        BigDecimal amount = new BigDecimal("500");
        BigDecimal newBalance = strategy.calculateNewBalance(testAccount, amount);
        
        assertEquals(new BigDecimal("1500"), newBalance);
    }
    
    @Test
    void testWithdrawalStrategy() {
        TransactionStrategy strategy = strategyFactory.getStrategy(Transaction.TransactionType.WITHDRAWAL);
        
        assertTrue(strategy.canProcess(Transaction.TransactionType.WITHDRAWAL));
        assertFalse(strategy.canProcess(Transaction.TransactionType.DEPOSIT));
        
        BigDecimal amount = new BigDecimal("300");
        BigDecimal newBalance = strategy.calculateNewBalance(testAccount, amount);
        
        assertEquals(new BigDecimal("700"), newBalance);
    }
    
    @Test
    void testInvalidDepositAmount() {
        TransactionStrategy strategy = strategyFactory.getStrategy(Transaction.TransactionType.DEPOSIT);
        
        assertThrows(IllegalArgumentException.class, () -> 
            strategy.validateTransaction(testAccount, new BigDecimal("-100"))
        );
    }
    
    @Test
    void testInsufficientFundsWithdrawal() {
        TransactionStrategy strategy = strategyFactory.getStrategy(Transaction.TransactionType.WITHDRAWAL);
        
        assertThrows(Exception.class, () -> 
            strategy.validateTransaction(testAccount, new BigDecimal("2000"))
        );
    }
}