package com.eaglebank.pattern.strategy;

import com.eaglebank.entity.Account;
import com.eaglebank.entity.Transaction;
import com.eaglebank.entity.User;
import com.eaglebank.exception.InsufficientFundsException;
import com.eaglebank.util.UuidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class WithdrawalStrategyTest {
    
    private WithdrawalStrategy withdrawalStrategy;
    private Account testAccount;
    
    @BeforeEach
    void setUp() {
        withdrawalStrategy = new WithdrawalStrategy();
        
        User user = User.builder()
                .id(UuidGenerator.generateUuidV7())
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .build();
        
        testAccount = Account.builder()
                .id(UuidGenerator.generateUuidV7())
                .accountNumber("TEST123456")
                .accountType(Account.AccountType.CHECKING)
                .balance(new BigDecimal("1000.00"))
                .status(Account.AccountStatus.ACTIVE)
                .user(user)
                .build();
    }
    
    @Test
    @DisplayName("Should correctly identify withdrawal transaction type")
    void shouldIdentifyWithdrawalType() {
        assertTrue(withdrawalStrategy.canProcess(Transaction.TransactionType.WITHDRAWAL));
        assertFalse(withdrawalStrategy.canProcess(Transaction.TransactionType.DEPOSIT));
    }
    
    @Test
    @DisplayName("Should validate withdrawal within available balance")
    void shouldValidateWithdrawalWithinBalance() {
        assertDoesNotThrow(() -> 
            withdrawalStrategy.validateTransaction(testAccount, new BigDecimal("500.00"))
        );
    }
    
    @Test
    @DisplayName("Should reject withdrawal exceeding available balance")
    void shouldRejectInsufficientFunds() {
        InsufficientFundsException exception = assertThrows(InsufficientFundsException.class, () -> 
            withdrawalStrategy.validateTransaction(testAccount, new BigDecimal("1500.00"))
        );
        
        assertTrue(exception.getMessage().contains("Insufficient funds"));
        assertTrue(exception.getMessage().contains("1000"));
        assertTrue(exception.getMessage().contains("1500"));
    }
    
    @Test
    @DisplayName("Should reject negative withdrawal amount")
    void shouldRejectNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> 
            withdrawalStrategy.validateTransaction(testAccount, new BigDecimal("-100.00"))
        );
    }
    
    @Test
    @DisplayName("Should reject withdrawal exceeding maximum limit")
    void shouldRejectExcessiveAmount() {
        testAccount.setBalance(new BigDecimal("100000.00"));
        
        assertThrows(IllegalArgumentException.class, () -> 
            withdrawalStrategy.validateTransaction(testAccount, new BigDecimal("60000.00"))
        );
    }
    
    @Test
    @DisplayName("Should reject withdrawal from inactive account")
    void shouldRejectWithdrawalFromInactiveAccount() {
        testAccount.setStatus(Account.AccountStatus.INACTIVE);
        
        assertThrows(IllegalStateException.class, () -> 
            withdrawalStrategy.validateTransaction(testAccount, new BigDecimal("100.00"))
        );
    }
    
    @Test
    @DisplayName("Should correctly calculate new balance after withdrawal")
    void shouldCalculateNewBalance() {
        BigDecimal withdrawalAmount = new BigDecimal("300.00");
        BigDecimal newBalance = withdrawalStrategy.calculateNewBalance(testAccount, withdrawalAmount);
        
        assertEquals(new BigDecimal("700.00"), newBalance);
        // Original balance should not be modified
        assertEquals(new BigDecimal("1000.00"), testAccount.getBalance());
    }
    
    @Test
    @DisplayName("Should allow withdrawal of entire balance")
    void shouldAllowFullBalanceWithdrawal() {
        BigDecimal withdrawalAmount = new BigDecimal("1000.00");
        
        assertDoesNotThrow(() -> 
            withdrawalStrategy.validateTransaction(testAccount, withdrawalAmount)
        );
        
        BigDecimal newBalance = withdrawalStrategy.calculateNewBalance(testAccount, withdrawalAmount);
        assertEquals(BigDecimal.ZERO, newBalance);
    }
    
    @Test
    @DisplayName("Should handle post-processing with low balance warning")
    void shouldHandlePostProcessingWithLowBalance() {
        Transaction transaction = Transaction.builder()
                .id(UuidGenerator.generateUuidV7())
                .amount(new BigDecimal("950.00"))
                .balanceAfter(new BigDecimal("50.00"))
                .account(testAccount)
                .build();
        
        assertDoesNotThrow(() -> withdrawalStrategy.postProcess(transaction));
    }
}