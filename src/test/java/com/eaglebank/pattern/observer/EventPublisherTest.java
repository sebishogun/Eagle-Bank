package com.eaglebank.pattern.observer;

import com.eaglebank.event.AccountCreatedEvent;
import com.eaglebank.event.TransactionCompletedEvent;
import com.eaglebank.event.UserLoggedInEvent;
import com.eaglebank.entity.Transaction;
import com.eaglebank.util.UuidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventPublisherTest {
    
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    
    @InjectMocks
    private EventPublisher eventPublisher;
    
    private UUID userId;
    private UUID accountId;
    
    @BeforeEach
    void setUp() {
        userId = UuidGenerator.generateUuidV7();
        accountId = UuidGenerator.generateUuidV7();
    }
    
    @Test
    @DisplayName("Should publish account created event")
    void shouldPublishAccountCreatedEvent() {
        AccountCreatedEvent event = new AccountCreatedEvent(
                accountId,
                userId,
                "SAV123456789",
                "SAVINGS",
                new BigDecimal("1000.00")
        );
        
        eventPublisher.publishEvent(event);
        
        ArgumentCaptor<AccountCreatedEvent> captor = ArgumentCaptor.forClass(AccountCreatedEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        
        AccountCreatedEvent published = captor.getValue();
        assertEquals(accountId, published.getAccountId());
        assertEquals(userId, published.getUserId());
        assertEquals("SAV123456789", published.getAccountNumber());
        assertEquals("SAVINGS", published.getAccountType());
        assertEquals(new BigDecimal("1000.00"), published.getInitialBalance());
        assertNotNull(published.getEventId());
        assertNotNull(published.getOccurredAt());
        assertEquals("ACCOUNT_CREATED", published.getEventType());
    }
    
    @Test
    @DisplayName("Should publish transaction completed event")
    void shouldPublishTransactionCompletedEvent() {
        UUID transactionId = UuidGenerator.generateUuidV7();
        String referenceNumber = "TXN20250804123456";
        
        TransactionCompletedEvent event = new TransactionCompletedEvent(
                transactionId,
                accountId,
                userId,
                referenceNumber,
                Transaction.TransactionType.DEPOSIT,
                new BigDecimal("500.00"),
                new BigDecimal("1500.00")
        );
        
        eventPublisher.publishEvent(event);
        
        ArgumentCaptor<TransactionCompletedEvent> captor = ArgumentCaptor.forClass(TransactionCompletedEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        
        TransactionCompletedEvent published = captor.getValue();
        assertEquals(transactionId, published.getTransactionId());
        assertEquals(accountId, published.getAccountId());
        assertEquals(userId, published.getUserId());
        assertEquals(referenceNumber, published.getReferenceNumber());
        assertEquals(Transaction.TransactionType.DEPOSIT, published.getType());
        assertEquals(new BigDecimal("500.00"), published.getAmount());
        assertEquals(new BigDecimal("1500.00"), published.getBalanceAfter());
        assertEquals("TRANSACTION_COMPLETED", published.getEventType());
    }
    
    @Test
    @DisplayName("Should publish user logged in event")
    void shouldPublishUserLoggedInEvent() {
        String username = "test@example.com";
        String ipAddress = "192.168.1.100";
        String userAgent = "Mozilla/5.0";
        
        UserLoggedInEvent event = new UserLoggedInEvent(
                userId,
                username,
                ipAddress,
                userAgent
        );
        
        eventPublisher.publishEvent(event);
        
        ArgumentCaptor<UserLoggedInEvent> captor = ArgumentCaptor.forClass(UserLoggedInEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        
        UserLoggedInEvent published = captor.getValue();
        assertEquals(userId, published.getUserId());
        assertEquals(username, published.getUsername());
        assertEquals(ipAddress, published.getIpAddress());
        assertEquals(userAgent, published.getUserAgent());
        assertEquals("USER_LOGGED_IN", published.getEventType());
    }
    
    @Test
    @DisplayName("Should handle null events gracefully")
    void shouldHandleNullEventsGracefully() {
        // Should not throw exception
        assertDoesNotThrow(() -> eventPublisher.publishEvent(null));
        
        // Verify publisher was called with null
        verify(applicationEventPublisher).publishEvent(null);
    }
}