package com.eaglebank.pattern.strategy;

import com.eaglebank.entity.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AccountStatusStrategyFactoryTest {
    
    private AccountStatusStrategyFactory factory;
    private ActiveAccountStrategy activeStrategy;
    private FrozenAccountStrategy frozenStrategy;
    private ClosedAccountStrategy closedStrategy;
    
    @BeforeEach
    void setUp() {
        activeStrategy = new ActiveAccountStrategy();
        frozenStrategy = new FrozenAccountStrategy();
        closedStrategy = new ClosedAccountStrategy();
        
        List<AccountStatusStrategy> strategies = Arrays.asList(
            activeStrategy,
            frozenStrategy,
            closedStrategy
        );
        
        factory = new AccountStatusStrategyFactory(strategies);
    }
    
    @Test
    @DisplayName("Should return correct strategy for ACTIVE status")
    void getStrategy_ActiveStatus_ReturnsActiveStrategy() {
        AccountStatusStrategy strategy = factory.getStrategy(Account.AccountStatus.ACTIVE);
        
        assertNotNull(strategy);
        assertInstanceOf(ActiveAccountStrategy.class, strategy);
        assertEquals(Account.AccountStatus.ACTIVE, strategy.getHandledStatus());
    }
    
    @Test
    @DisplayName("Should return correct strategy for FROZEN status")
    void getStrategy_FrozenStatus_ReturnsFrozenStrategy() {
        AccountStatusStrategy strategy = factory.getStrategy(Account.AccountStatus.FROZEN);
        
        assertNotNull(strategy);
        assertInstanceOf(FrozenAccountStrategy.class, strategy);
        assertEquals(Account.AccountStatus.FROZEN, strategy.getHandledStatus());
    }
    
    @Test
    @DisplayName("Should return correct strategy for CLOSED status")
    void getStrategy_ClosedStatus_ReturnsClosedStrategy() {
        AccountStatusStrategy strategy = factory.getStrategy(Account.AccountStatus.CLOSED);
        
        assertNotNull(strategy);
        assertInstanceOf(ClosedAccountStrategy.class, strategy);
        assertEquals(Account.AccountStatus.CLOSED, strategy.getHandledStatus());
    }
    
    @Test
    @DisplayName("Should return correct strategy for account object")
    void getStrategy_WithAccountObject_ReturnsCorrectStrategy() {
        Account account = Account.builder()
                .status(Account.AccountStatus.FROZEN)
                .build();
                
        AccountStatusStrategy strategy = factory.getStrategy(account);
        
        assertNotNull(strategy);
        assertInstanceOf(FrozenAccountStrategy.class, strategy);
    }
    
    @Test
    @DisplayName("Should throw exception for null status")
    void getStrategy_NullStatus_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            factory.getStrategy((Account.AccountStatus) null);
        });
    }
    
    @Test
    @DisplayName("Should initialize with all strategies")
    void constructor_InitializesAllStrategies() {
        // This is tested implicitly by the other tests, but let's verify
        // all three strategies are accessible
        assertDoesNotThrow(() -> {
            factory.getStrategy(Account.AccountStatus.ACTIVE);
            factory.getStrategy(Account.AccountStatus.FROZEN);
            factory.getStrategy(Account.AccountStatus.CLOSED);
        });
    }
    
    @Test
    @DisplayName("Should handle empty strategy list")
    void constructor_EmptyStrategyList_HandlesGracefully() {
        AccountStatusStrategyFactory emptyFactory = new AccountStatusStrategyFactory(List.of());
        
        assertThrows(IllegalArgumentException.class, () -> {
            emptyFactory.getStrategy(Account.AccountStatus.ACTIVE);
        });
    }
}