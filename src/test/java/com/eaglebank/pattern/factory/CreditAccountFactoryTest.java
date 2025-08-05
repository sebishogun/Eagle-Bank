package com.eaglebank.pattern.factory;

import com.eaglebank.entity.Account;
import com.eaglebank.entity.User;
import com.eaglebank.util.UuidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CreditAccountFactoryTest {
    
    private CreditAccountFactory factory;
    private User testUser;
    
    @BeforeEach
    void setUp() {
        factory = new CreditAccountFactory();
        testUser = User.builder()
                .id(UuidGenerator.generateUuidV7())
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .build();
    }
    
    @Test
    @DisplayName("Should create credit account with default credit limit")
    void shouldCreateCreditAccountWithDefaultLimit() {
        Account account = factory.createAccount(testUser, BigDecimal.ZERO);
        
        assertNotNull(account);
        assertEquals(Account.AccountType.CREDIT, account.getAccountType());
        assertEquals("Credit Account", account.getAccountName());
        assertEquals(BigDecimal.ZERO, account.getBalance());
        assertEquals(new BigDecimal("5000.00"), account.getCreditLimit());
        assertTrue(account.getAccountNumber().startsWith("4"));
        assertEquals(16, account.getAccountNumber().length());
    }
    
    @Test
    @DisplayName("Should have correct account type")
    void shouldHaveCorrectAccountType() {
        assertEquals("CREDIT", factory.getAccountType());
    }
    
    @Test
    @DisplayName("Should have negative minimum balance equal to credit limit")
    void shouldHaveNegativeMinimumBalance() {
        BigDecimal minBalance = factory.getMinimumBalance();
        assertEquals(new BigDecimal("-5000.00"), minBalance);
    }
    
    @Test
    @DisplayName("Should have large maximum balance")
    void shouldHaveLargeMaximumBalance() {
        BigDecimal maxBalance = factory.getMaximumBalance();
        assertTrue(maxBalance.compareTo(new BigDecimal("999999999")) == 0);
    }
    
    @Test
    @DisplayName("Should validate initial balance must be zero")
    void shouldValidateInitialBalanceMustBeZero() {
        // Valid case - zero balance
        assertDoesNotThrow(() -> factory.validateInitialBalance(BigDecimal.ZERO));
        
        // Invalid cases - non-zero balance
        assertThrows(IllegalArgumentException.class, 
            () -> factory.validateInitialBalance(new BigDecimal("100.00")));
        assertThrows(IllegalArgumentException.class, 
            () -> factory.validateInitialBalance(new BigDecimal("-100.00")));
    }
    
    @Test
    @DisplayName("Should create unique account numbers")
    void shouldCreateUniqueAccountNumbers() {
        Account account1 = factory.createAccount(testUser, BigDecimal.ZERO);
        Account account2 = factory.createAccount(testUser, BigDecimal.ZERO);
        
        assertNotEquals(account1.getAccountNumber(), account2.getAccountNumber());
    }
}