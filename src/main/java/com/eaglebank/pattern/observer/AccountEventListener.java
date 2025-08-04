package com.eaglebank.pattern.observer;

import com.eaglebank.event.AccountCreatedEvent;
import com.eaglebank.event.TransactionCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEventListener {
    
    @EventListener
    @Async
    public void handleAccountCreated(AccountCreatedEvent event) {
        log.info("Account created: {} for user: {} with initial balance: {}", 
                event.getAccountNumber(), event.getUserId(), event.getInitialBalance());
        
        // Could send welcome email, create initial statements, etc.
        if (event.getInitialBalance().compareTo(new BigDecimal("10000")) > 0) {
            log.info("High-value account created with balance: {}", event.getInitialBalance());
            // Could trigger special onboarding process
        }
    }
    
    @EventListener
    @Async
    public void handleTransactionCompleted(TransactionCompletedEvent event) {
        log.info("Transaction completed: {} - {} {} for account: {}", 
                event.getReferenceNumber(), event.getType(), event.getAmount(), event.getAccountId());
        
        // Check for suspicious activity
        if (event.getAmount().compareTo(new BigDecimal("50000")) > 0) {
            log.warn("Large transaction detected: {} - Amount: {}", 
                    event.getReferenceNumber(), event.getAmount());
            // Could trigger fraud detection
        }
        
        // Check for low balance
        if (event.getBalanceAfter().compareTo(new BigDecimal("100")) < 0) {
            log.warn("Low balance alert for account: {} - Balance: {}", 
                    event.getAccountId(), event.getBalanceAfter());
            // Could send low balance notification
        }
    }
}