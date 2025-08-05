package com.eaglebank.pattern.strategy;

import com.eaglebank.entity.Account;
import com.eaglebank.entity.Transaction;
import com.eaglebank.entity.User;
import com.eaglebank.exception.InsufficientFundsException;
import com.eaglebank.util.UuidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class CreditWithdrawalStrategyTest {
    
    private CreditWithdrawalStrategy strategy;
    private Account creditAccount;
    private User user;
    
    @BeforeEach
    void setUp() {
        strategy = new CreditWithdrawalStrategy();
        
        user = User.builder()
                .id(UuidGenerator.generateUuidV7())
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .build();
        
        creditAccount = Account.builder()
                .id(UuidGenerator.generateUuidV7())
                .accountNumber("4111111111111111")
                .accountName("Test Credit Card")
                .accountType(Account.AccountType.CREDIT)
                .balance(BigDecimal.ZERO)
                .creditLimit(new BigDecimal("1000.00"))
                .status(Account.AccountStatus.ACTIVE)
                .user(user)
                .build();
    }
    
    @Test
    @DisplayName("Should handle withdrawal transaction type")
    void shouldHandleWithdrawalType() {
        assertTrue(strategy.canProcess(Transaction.TransactionType.WITHDRAWAL));
        assertFalse(strategy.canProcess(Transaction.TransactionType.DEPOSIT));
    }
    
    @Test
    @DisplayName("Should validate withdrawal within credit limit")
    void shouldValidateWithdrawalWithinLimit() {
        // Test withdrawal of $500 with $1000 credit limit
        assertDoesNotThrow(() -> 
            strategy.validateTransaction(creditAccount, new BigDecimal("500.00"))
        );
        
        // Test withdrawal of $1000 (exactly at limit)
        assertDoesNotThrow(() -> 
            strategy.validateTransaction(creditAccount, new BigDecimal("1000.00"))
        );
    }
    
    @Test
    @DisplayName("Should reject withdrawal exceeding credit limit")
    void shouldRejectWithdrawalExceedingLimit() {
        // Test withdrawal of $1500 with $1000 credit limit
        InsufficientFundsException exception = assertThrows(
            InsufficientFundsException.class,
            () -> strategy.validateTransaction(creditAccount, new BigDecimal("1500.00"))
        );
        
        assertTrue(exception.getMessage().contains("Insufficient credit"));
        assertTrue(exception.getMessage().contains("Available credit: 1000.00"));
        assertTrue(exception.getMessage().contains("Requested: 1500.00"));
    }
    
    @Test
    @DisplayName("Should validate with existing credit usage")
    void shouldValidateWithExistingCreditUsage() {
        // Set balance to -$600 (already used $600 of credit)
        creditAccount.setBalance(new BigDecimal("-600.00"));
        
        // Should allow $400 more
        assertDoesNotThrow(() -> 
            strategy.validateTransaction(creditAccount, new BigDecimal("400.00"))
        );
        
        // Should reject $500 (would exceed limit)
        InsufficientFundsException exception = assertThrows(
            InsufficientFundsException.class,
            () -> strategy.validateTransaction(creditAccount, new BigDecimal("500.00"))
        );
        
        assertTrue(exception.getMessage().contains("Available credit: 400.00"));
    }
    
    @Test
    @DisplayName("Should reject for non-credit accounts")
    void shouldRejectForNonCreditAccounts() {
        Account savingsAccount = Account.builder()
                .id(UuidGenerator.generateUuidV7())
                .accountNumber("SAV123456")
                .accountType(Account.AccountType.SAVINGS)
                .balance(new BigDecimal("1000.00"))
                .status(Account.AccountStatus.ACTIVE)
                .user(user)
                .build();
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> strategy.validateTransaction(savingsAccount, new BigDecimal("100.00"))
        );
        
        assertEquals("This strategy is only for credit accounts", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should calculate new balance correctly")
    void shouldCalculateNewBalanceCorrectly() {
        // Starting balance: $0
        BigDecimal newBalance = strategy.calculateNewBalance(creditAccount, new BigDecimal("250.00"));
        assertEquals(new BigDecimal("-250.00"), newBalance);
        
        // With existing credit usage
        creditAccount.setBalance(new BigDecimal("-300.00"));
        newBalance = strategy.calculateNewBalance(creditAccount, new BigDecimal("200.00"));
        assertEquals(new BigDecimal("-500.00"), newBalance);
    }
    
    @Test
    @DisplayName("Should generate appropriate transaction description")
    void shouldGenerateTransactionDescription() {
        String description = strategy.getTransactionDescription(new BigDecimal("123.45"));
        assertEquals("Credit withdrawal of 123.45", description);
    }
    
    @Test
    @DisplayName("Should handle null credit limit")
    void shouldHandleNullCreditLimit() {
        creditAccount.setCreditLimit(null);
        
        // Should treat null credit limit as zero
        InsufficientFundsException exception = assertThrows(
            InsufficientFundsException.class,
            () -> strategy.validateTransaction(creditAccount, new BigDecimal("1.00"))
        );
        
        assertTrue(exception.getMessage().contains("Available credit: 0.00"));
    }
    
    @Test
    @DisplayName("Should process post-transaction correctly")
    void shouldProcessPostTransaction() {
        Transaction transaction = Transaction.builder()
                .id(UuidGenerator.generateUuidV7())
                .referenceNumber("TXN123456")
                .type(Transaction.TransactionType.WITHDRAWAL)
                .amount(new BigDecimal("500.00"))
                .balanceAfter(new BigDecimal("-500.00"))
                .account(creditAccount)
                .transactionDate(LocalDateTime.now())
                .build();
        
        // Should not throw exception
        assertDoesNotThrow(() -> strategy.postProcess(transaction));
    }
}