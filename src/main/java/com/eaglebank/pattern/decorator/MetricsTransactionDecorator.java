package com.eaglebank.pattern.decorator;

import com.eaglebank.dto.request.CreateTransactionRequest;
import com.eaglebank.dto.response.TransactionResponse;
import com.eaglebank.entity.Transaction;
import com.eaglebank.metrics.TransactionMetricsCollector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
public class MetricsTransactionDecorator extends TransactionDecorator {
    
    private final TransactionMetricsCollector metricsCollector;
    
    public MetricsTransactionDecorator(TransactionProcessor processor, 
                                       TransactionMetricsCollector metricsCollector) {
        super(processor);
        this.metricsCollector = metricsCollector;
    }
    
    @Override
    public TransactionResponse process(UUID userId, UUID accountId, CreateTransactionRequest request) {
        LocalDateTime startTime = LocalDateTime.now();
        
        try {
            TransactionResponse response = super.process(userId, accountId, request);
            
            // Record metrics
            long processingTime = Duration.between(startTime, LocalDateTime.now()).toMillis();
            metricsCollector.recordTransaction(
                request.getTransactionType(),
                request.getAmount(),
                processingTime
            );
            
            log.debug("Transaction processed in {}ms", processingTime);
            
            return response;
        } catch (Exception e) {
            // Still record metrics for failed transactions
            long processingTime = Duration.between(startTime, LocalDateTime.now()).toMillis();
            log.warn("Transaction failed after {}ms: {}", processingTime, e.getMessage());
            throw e;
        }
    }
}