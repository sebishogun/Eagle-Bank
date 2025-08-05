package com.eaglebank.pattern.factory;

import com.eaglebank.entity.Account;
import com.eaglebank.entity.User;
import com.eaglebank.util.UuidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class SavingsAccountFactoryTest {
    
    private SavingsAccountFactory factory;
    private User testUser;
    
    @BeforeEach
    void setUp() {
        factory = new SavingsAccountFactory();
        
        testUser = User.builder()
                .id(UuidGenerator.generateUuidV7())
                .email("test@example.com")
                .firstName("John")
                .lastName("Doe")
                .build();
    }
    
    @Test
    @DisplayName("Should create savings account with valid initial balance")
    void shouldCreateSavingsAccount() {
        BigDecimal initialBalance = new BigDecimal("500.00");
        
        Account account = factory.createAccount(testUser, initialBalance);
        
        assertNotNull(account);
        assertNotNull(account.getId());
        assertEquals(Account.AccountType.SAVINGS, account.getAccountType());
        assertEquals(initialBalance, account.getBalance());
        assertEquals("USD", account.getCurrency());
        assertEquals(Account.AccountStatus.ACTIVE, account.getStatus());
        assertEquals(testUser, account.getUser());
        assertEquals("John Doe Savings", account.getAccountName());
        assertTrue(account.getAccountNumber().startsWith("SAV"));
        assertEquals(13, account.getAccountNumber().length());
    }
    
    @Test
    @DisplayName("Should return correct account type")
    void shouldReturnCorrectAccountType() {
        assertEquals("SAVINGS", factory.getAccountType());
    }
    
    @Test
    @DisplayName("Should return correct minimum balance")
    void shouldReturnCorrectMinimumBalance() {
        assertEquals(new BigDecimal("100"), factory.getMinimumBalance());
    }
    
    @Test
    @DisplayName("Should return correct maximum balance")
    void shouldReturnCorrectMaximumBalance() {
        assertEquals(new BigDecimal("10000000"), factory.getMaximumBalance());
    }
    
    @Test
    @DisplayName("Should reject initial balance below minimum")
    void shouldRejectBalanceBelowMinimum() {
        BigDecimal belowMinimum = new BigDecimal("50.00");
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> 
            factory.createAccount(testUser, belowMinimum)
        );
        
        assertTrue(exception.getMessage().contains("at least"));
        assertTrue(exception.getMessage().contains("100"));
    }
    
    @Test
    @DisplayName("Should reject initial balance above maximum")
    void shouldRejectBalanceAboveMaximum() {
        BigDecimal aboveMaximum = new BigDecimal("20000000.00");
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> 
            factory.createAccount(testUser, aboveMaximum)
        );
        
        assertTrue(exception.getMessage().contains("cannot exceed"));
        assertTrue(exception.getMessage().contains("10000000"));
    }
    
    @Test
    @DisplayName("Should validate initial balance before account creation")
    void shouldValidateInitialBalance() {
        assertThrows(IllegalArgumentException.class, () -> 
            factory.validateInitialBalance(new BigDecimal("50.00"))
        );
        
        assertThrows(IllegalArgumentException.class, () -> 
            factory.validateInitialBalance(new BigDecimal("20000000.00"))
        );
        
        assertDoesNotThrow(() -> 
            factory.validateInitialBalance(new BigDecimal("1000.00"))
        );
    }
    
    @Test
    @DisplayName("Should generate unique account numbers")
    void shouldGenerateUniqueAccountNumbers() {
        Account account1 = factory.createAccount(testUser, new BigDecimal("1000.00"));
        Account account2 = factory.createAccount(testUser, new BigDecimal("2000.00"));
        
        assertNotEquals(account1.getAccountNumber(), account2.getAccountNumber());
        assertTrue(account1.getAccountNumber().startsWith("SAV"));
        assertTrue(account2.getAccountNumber().startsWith("SAV"));
    }
}