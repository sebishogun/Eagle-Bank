package com.eaglebank.pattern.factory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AccountFactoryProviderTest {
    
    private AccountFactoryProvider provider;
    
    @BeforeEach
    void setUp() {
        SavingsAccountFactory savingsFactory = new SavingsAccountFactory();
        CheckingAccountFactory checkingFactory = new CheckingAccountFactory();
        
        provider = new AccountFactoryProvider(List.of(savingsFactory, checkingFactory));
        provider.init();
    }
    
    @Test
    @DisplayName("Should return savings factory for SAVINGS type")
    void shouldReturnSavingsFactory() {
        AccountFactory factory = provider.getFactory("SAVINGS");
        
        assertNotNull(factory);
        assertInstanceOf(SavingsAccountFactory.class, factory);
        assertEquals("SAVINGS", factory.getAccountType());
    }
    
    @Test
    @DisplayName("Should return checking factory for CHECKING type")
    void shouldReturnCheckingFactory() {
        AccountFactory factory = provider.getFactory("CHECKING");
        
        assertNotNull(factory);
        assertInstanceOf(CheckingAccountFactory.class, factory);
        assertEquals("CHECKING", factory.getAccountType());
    }
    
    @Test
    @DisplayName("Should handle case-insensitive account types")
    void shouldHandleCaseInsensitiveTypes() {
        assertNotNull(provider.getFactory("savings"));
        assertNotNull(provider.getFactory("SAVINGS"));
        assertNotNull(provider.getFactory("Savings"));
        assertNotNull(provider.getFactory("checking"));
        assertNotNull(provider.getFactory("CHECKING"));
    }
    
    @Test
    @DisplayName("Should throw exception for unknown account type")
    void shouldThrowExceptionForUnknownType() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> 
            provider.getFactory("UNKNOWN")
        );
        
        assertTrue(exception.getMessage().contains("Unknown account type"));
        assertTrue(exception.getMessage().contains("UNKNOWN"));
    }
    
    @Test
    @DisplayName("Should return list of available account types")
    void shouldReturnAvailableAccountTypes() {
        List<String> types = provider.getAvailableAccountTypes();
        
        assertNotNull(types);
        assertEquals(2, types.size());
        assertTrue(types.contains("SAVINGS"));
        assertTrue(types.contains("CHECKING"));
    }
}