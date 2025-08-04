package com.eaglebank.pattern.decorator;

import com.eaglebank.dto.request.CreateTransactionRequest;
import com.eaglebank.dto.response.TransactionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
public class LoggingTransactionDecorator extends TransactionDecorator {
    
    public LoggingTransactionDecorator(TransactionProcessor processor) {
        super(processor);
    }
    
    @Override
    public TransactionResponse process(UUID userId, UUID accountId, CreateTransactionRequest request) {
        LocalDateTime startTime = LocalDateTime.now();
        
        log.info("Transaction processing started - User: {}, Account: {}, Type: {}, Amount: {}",
                userId, accountId, request.getTransactionType(), request.getAmount());
        
        try {
            TransactionResponse response = super.process(userId, accountId, request);
            
            log.info("Transaction processing completed successfully - Reference: {}, Time taken: {}ms",
                    response.getTransactionReference(),
                    java.time.Duration.between(startTime, LocalDateTime.now()).toMillis());
            
            return response;
        } catch (Exception e) {
            log.error("Transaction processing failed - User: {}, Account: {}, Error: {}",
                    userId, accountId, e.getMessage());
            throw e;
        }
    }
}