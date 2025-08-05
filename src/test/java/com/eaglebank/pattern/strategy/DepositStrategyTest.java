package com.eaglebank.pattern.strategy;

import com.eaglebank.entity.Account;
import com.eaglebank.entity.Transaction;
import com.eaglebank.entity.User;
import com.eaglebank.util.UuidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class DepositStrategyTest {
    
    private DepositStrategy depositStrategy;
    private Account testAccount;
    
    @BeforeEach
    void setUp() {
        depositStrategy = new DepositStrategy();
        
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
                .balance(new BigDecimal("1000.00"))
                .status(Account.AccountStatus.ACTIVE)
                .user(user)
                .build();
    }
    
    @Test
    @DisplayName("Should correctly identify deposit transaction type")
    void shouldIdentifyDepositType() {
        assertTrue(depositStrategy.canProcess(Transaction.TransactionType.DEPOSIT));
        assertFalse(depositStrategy.canProcess(Transaction.TransactionType.WITHDRAWAL));
    }
    
    @Test
    @DisplayName("Should validate positive deposit amount")
    void shouldValidatePositiveAmount() {
        assertDoesNotThrow(() -> 
            depositStrategy.validateTransaction(testAccount, new BigDecimal("100.00"))
        );
    }
    
    @Test
    @DisplayName("Should reject negative deposit amount")
    void shouldRejectNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> 
            depositStrategy.validateTransaction(testAccount, new BigDecimal("-100.00"))
        );
    }
    
    @Test
    @DisplayName("Should reject zero deposit amount")
    void shouldRejectZeroAmount() {
        assertThrows(IllegalArgumentException.class, () -> 
            depositStrategy.validateTransaction(testAccount, BigDecimal.ZERO)
        );
    }
    
    @Test
    @DisplayName("Should reject deposit exceeding maximum limit")
    void shouldRejectExcessiveAmount() {
        assertThrows(IllegalArgumentException.class, () -> 
            depositStrategy.validateTransaction(testAccount, new BigDecimal("2000000.00"))
        );
    }
    
    @Test
    @DisplayName("Should reject deposit to inactive account")
    void shouldRejectDepositToInactiveAccount() {
        testAccount.setStatus(Account.AccountStatus.INACTIVE);
        
        assertThrows(IllegalStateException.class, () -> 
            depositStrategy.validateTransaction(testAccount, new BigDecimal("100.00"))
        );
    }
    
    @Test
    @DisplayName("Should correctly calculate new balance after deposit")
    void shouldCalculateNewBalance() {
        BigDecimal depositAmount = new BigDecimal("500.00");
        BigDecimal newBalance = depositStrategy.calculateNewBalance(testAccount, depositAmount);
        
        assertEquals(new BigDecimal("1500.00"), newBalance);
        // Original balance should not be modified
        assertEquals(new BigDecimal("1000.00"), testAccount.getBalance());
    }
    
    @Test
    @DisplayName("Should generate appropriate transaction description")
    void shouldGenerateDescription() {
        String description = depositStrategy.getTransactionDescription(new BigDecimal("100.00"));
        
        assertNotNull(description);
        assertTrue(description.contains("Deposit"));
        assertTrue(description.contains("100"));
    }
    
    @Test
    @DisplayName("Should handle post-processing without errors")
    void shouldHandlePostProcessing() {
        Transaction transaction = Transaction.builder()
                .id(UuidGenerator.generateUuidV7())
                .amount(new BigDecimal("15000.00"))
                .account(testAccount)
                .build();
        
        assertDoesNotThrow(() -> depositStrategy.postProcess(transaction));
    }
}