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

import static org.junit.jupiter.api.Assertions.*;

class TransferStrategyTest {
    
    private TransferStrategy transferStrategy;
    private Account testAccount;
    private User testUser;
    
    @BeforeEach
    void setUp() {
        transferStrategy = new TransferStrategy();
        
        testUser = User.builder()
                .id(UuidGenerator.generateUuidV7())
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .build();
        
        testAccount = Account.builder()
                .id(UuidGenerator.generateUuidV7())
                .accountNumber("ACC123456")
                .accountName("Test Account")
                .accountType(Account.AccountType.CHECKING)
                .balance(new BigDecimal("1000.00"))
                .status(Account.AccountStatus.ACTIVE)
                .user(testUser)
                .build();
    }
    
    @Test
    @DisplayName("Should process TRANSFER transaction type")
    void shouldProcessTransferType() {
        assertTrue(transferStrategy.canProcess(Transaction.TransactionType.TRANSFER));
        assertFalse(transferStrategy.canProcess(Transaction.TransactionType.DEPOSIT));
        assertFalse(transferStrategy.canProcess(Transaction.TransactionType.WITHDRAWAL));
    }
    
    @Test
    @DisplayName("Should validate positive transfer amount")
    void shouldValidatePositiveAmount() {
        assertDoesNotThrow(() -> 
            transferStrategy.validateTransaction(testAccount, new BigDecimal("100.00"))
        );
    }
    
    @Test
    @DisplayName("Should reject negative transfer amount")
    void shouldRejectNegativeAmount() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> transferStrategy.validateTransaction(testAccount, new BigDecimal("-100.00"))
        );
        assertEquals("Transfer amount must be positive", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should reject zero transfer amount")
    void shouldRejectZeroAmount() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> transferStrategy.validateTransaction(testAccount, BigDecimal.ZERO)
        );
        assertEquals("Transfer amount must be positive", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should reject transfer from inactive account")
    void shouldRejectTransferFromInactiveAccount() {
        testAccount.setStatus(Account.AccountStatus.INACTIVE);
        
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> transferStrategy.validateTransaction(testAccount, new BigDecimal("100.00"))
        );
        assertEquals("Source account must be active for transfers", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should reject transfer from frozen account")
    void shouldRejectTransferFromFrozenAccount() {
        testAccount.setStatus(Account.AccountStatus.FROZEN);
        
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> transferStrategy.validateTransaction(testAccount, new BigDecimal("100.00"))
        );
        assertEquals("Source account must be active for transfers", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should reject transfer from closed account")
    void shouldRejectTransferFromClosedAccount() {
        testAccount.setStatus(Account.AccountStatus.CLOSED);
        
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> transferStrategy.validateTransaction(testAccount, new BigDecimal("100.00"))
        );
        assertEquals("Source account must be active for transfers", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should reject transfer with insufficient funds")
    void shouldRejectInsufficientFunds() {
        InsufficientFundsException exception = assertThrows(
            InsufficientFundsException.class,
            () -> transferStrategy.validateTransaction(testAccount, new BigDecimal("2000.00"))
        );
        assertTrue(exception.getMessage().contains("Insufficient funds"));
        assertTrue(exception.getMessage().contains("1000"));
        assertTrue(exception.getMessage().contains("2000"));
    }
    
    @Test
    @DisplayName("Should allow transfer with exact balance")
    void shouldAllowTransferWithExactBalance() {
        assertDoesNotThrow(() -> 
            transferStrategy.validateTransaction(testAccount, new BigDecimal("1000.00"))
        );
    }
    
    @Test
    @DisplayName("Should calculate new balance correctly")
    void shouldCalculateNewBalanceCorrectly() {
        BigDecimal newBalance = transferStrategy.calculateNewBalance(
            testAccount, 
            new BigDecimal("250.00")
        );
        assertEquals(new BigDecimal("750.00"), newBalance);
    }
    
    @Test
    @DisplayName("Should calculate new balance with full amount")
    void shouldCalculateNewBalanceWithFullAmount() {
        BigDecimal newBalance = transferStrategy.calculateNewBalance(
            testAccount, 
            new BigDecimal("1000.00")
        );
        assertEquals(new BigDecimal("0.00"), newBalance);
    }
    
    @Test
    @DisplayName("Should handle decimal precision in balance calculation")
    void shouldHandleDecimalPrecision() {
        testAccount.setBalance(new BigDecimal("100.50"));
        BigDecimal newBalance = transferStrategy.calculateNewBalance(
            testAccount, 
            new BigDecimal("50.25")
        );
        assertEquals(new BigDecimal("50.25"), newBalance);
    }
    
    @Test
    @DisplayName("Should generate transfer description")
    void shouldGenerateTransferDescription() {
        String description = transferStrategy.getTransactionDescription(new BigDecimal("500.00"));
        assertEquals("Transfer of 500.00", description);
    }
    
    @Test
    @DisplayName("Should post-process transfer transaction")
    void shouldPostProcessTransfer() {
        Transaction transaction = Transaction.builder()
                .id(UuidGenerator.generateUuidV7())
                .referenceNumber("TRF123456")
                .type(Transaction.TransactionType.TRANSFER)
                .amount(new BigDecimal("100.00"))
                .build();
        
        // Should not throw exception
        assertDoesNotThrow(() -> transferStrategy.postProcess(transaction));
    }
}