package com.eaglebank.pattern.strategy;

import com.eaglebank.entity.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransactionStrategyFactoryTest {
    
    private TransactionStrategyFactory factory;
    
    @BeforeEach
    void setUp() {
        DepositStrategy depositStrategy = new DepositStrategy();
        WithdrawalStrategy withdrawalStrategy = new WithdrawalStrategy();
        
        factory = new TransactionStrategyFactory(List.of(depositStrategy, withdrawalStrategy));
    }
    
    @Test
    @DisplayName("Should return correct strategy for deposit type")
    void shouldReturnDepositStrategy() {
        TransactionStrategy strategy = factory.getStrategy(Transaction.TransactionType.DEPOSIT);
        
        assertNotNull(strategy);
        assertInstanceOf(DepositStrategy.class, strategy);
        assertTrue(strategy.canProcess(Transaction.TransactionType.DEPOSIT));
    }
    
    @Test
    @DisplayName("Should return correct strategy for withdrawal type")
    void shouldReturnWithdrawalStrategy() {
        TransactionStrategy strategy = factory.getStrategy(Transaction.TransactionType.WITHDRAWAL);
        
        assertNotNull(strategy);
        assertInstanceOf(WithdrawalStrategy.class, strategy);
        assertTrue(strategy.canProcess(Transaction.TransactionType.WITHDRAWAL));
    }
    
    @Test
    @DisplayName("Should throw exception for unsupported transaction type")
    void shouldThrowExceptionForUnsupportedType() {
        // Create a factory with no strategies
        TransactionStrategyFactory emptyFactory = new TransactionStrategyFactory(List.of());
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> 
            emptyFactory.getStrategy(Transaction.TransactionType.DEPOSIT)
        );
        
        assertTrue(exception.getMessage().contains("No strategy found"));
    }
}