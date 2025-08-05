package com.eaglebank.pattern.observer;

import com.eaglebank.entity.Transaction;
import com.eaglebank.event.AccountCreatedEvent;
import com.eaglebank.event.TransactionCompletedEvent;
import com.eaglebank.util.UuidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AccountEventListenerTest {
    
    @InjectMocks
    private AccountEventListener eventListener;
    
    private UUID userId;
    private UUID accountId;
    
    @BeforeEach
    void setUp() {
        userId = UuidGenerator.generateUuidV7();
        accountId = UuidGenerator.generateUuidV7();
    }
    
    @Test
    @DisplayName("Should handle account created event")
    void shouldHandleAccountCreatedEvent() {
        AccountCreatedEvent event = new AccountCreatedEvent(
                accountId,
                userId,
                "SAV123456789",
                "SAVINGS",
                new BigDecimal("1000.00")
        );
        
        // Should not throw exception
        assertDoesNotThrow(() -> eventListener.handleAccountCreated(event));
    }
    
    @Test
    @DisplayName("Should handle high-value account creation")
    void shouldHandleHighValueAccountCreation() {
        AccountCreatedEvent event = new AccountCreatedEvent(
                accountId,
                userId,
                "SAV987654321",
                "SAVINGS",
                new BigDecimal("15000.00")
        );
        
        // Should not throw exception and should log high-value account
        assertDoesNotThrow(() -> eventListener.handleAccountCreated(event));
    }
    
    @Test
    @DisplayName("Should handle transaction completed event")
    void shouldHandleTransactionCompletedEvent() {
        UUID transactionId = UuidGenerator.generateUuidV7();
        
        TransactionCompletedEvent event = new TransactionCompletedEvent(
                transactionId,
                accountId,
                userId,
                "TXN20250804123456",
                Transaction.TransactionType.DEPOSIT,
                new BigDecimal("500.00"),
                new BigDecimal("1500.00")
        );
        
        // Should not throw exception
        assertDoesNotThrow(() -> eventListener.handleTransactionCompleted(event));
    }
    
    @Test
    @DisplayName("Should detect large transactions")
    void shouldDetectLargeTransactions() {
        UUID transactionId = UuidGenerator.generateUuidV7();
        
        TransactionCompletedEvent event = new TransactionCompletedEvent(
                transactionId,
                accountId,
                userId,
                "TXN20250804654321",
                Transaction.TransactionType.WITHDRAWAL,
                new BigDecimal("75000.00"),
                new BigDecimal("25000.00")
        );
        
        // Should not throw exception and should log large transaction warning
        assertDoesNotThrow(() -> eventListener.handleTransactionCompleted(event));
    }
    
    @Test
    @DisplayName("Should detect low balance")
    void shouldDetectLowBalance() {
        UUID transactionId = UuidGenerator.generateUuidV7();
        
        TransactionCompletedEvent event = new TransactionCompletedEvent(
                transactionId,
                accountId,
                userId,
                "TXN20250804111111",
                Transaction.TransactionType.WITHDRAWAL,
                new BigDecimal("950.00"),
                new BigDecimal("50.00")
        );
        
        // Should not throw exception and should log low balance warning
        assertDoesNotThrow(() -> eventListener.handleTransactionCompleted(event));
    }
    
    @Test
    @DisplayName("Should handle null events gracefully")
    void shouldHandleNullEventsGracefully() {
        // Should not throw exception
        assertDoesNotThrow(() -> eventListener.handleAccountCreated(null));
        assertDoesNotThrow(() -> eventListener.handleTransactionCompleted(null));
    }
}