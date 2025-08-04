package com.eaglebank.pattern.decorator;

import com.eaglebank.dto.request.CreateTransactionRequest;
import com.eaglebank.dto.response.TransactionResponse;
import com.eaglebank.entity.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Component
public class NotificationTransactionDecorator extends TransactionDecorator {
    
    private static final BigDecimal LARGE_TRANSACTION_THRESHOLD = new BigDecimal("10000");
    private static final BigDecimal LOW_BALANCE_THRESHOLD = new BigDecimal("100");
    
    public NotificationTransactionDecorator(TransactionProcessor processor) {
        super(processor);
    }
    
    @Override
    public TransactionResponse process(UUID userId, UUID accountId, CreateTransactionRequest request) {
        TransactionResponse response = super.process(userId, accountId, request);
        
        // Send notifications based on transaction details
        sendNotifications(response, request);
        
        return response;
    }
    
    private void sendNotifications(TransactionResponse response, CreateTransactionRequest request) {
        // Large transaction notification
        if (response.getAmount().compareTo(LARGE_TRANSACTION_THRESHOLD) > 0) {
            log.info("Sending large transaction notification for reference: {}", 
                    response.getTransactionReference());
            // In real implementation, would send email/SMS
        }
        
        // Low balance notification
        if (response.getTransactionType() == Transaction.TransactionType.WITHDRAWAL &&
            response.getBalanceAfter().compareTo(LOW_BALANCE_THRESHOLD) < 0) {
            log.info("Sending low balance notification for account: {}", response.getAccountId());
            // In real implementation, would send email/SMS
        }
        
        // Transaction confirmation
        log.info("Sending transaction confirmation for reference: {}", 
                response.getTransactionReference());
        // In real implementation, would send email/SMS
    }
}