package com.eaglebank.pattern.chain;

import com.eaglebank.dto.request.CreateTransactionRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
public class AmountValidationHandler extends ValidationHandler<CreateTransactionRequest> {
    
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000");
    
    @Override
    protected boolean canHandle(CreateTransactionRequest request) {
        return request.getAmount() != null;
    }
    
    @Override
    protected void doValidate(CreateTransactionRequest request) {
        BigDecimal amount = request.getAmount();
        
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amount must be positive");
        }
        
        if (amount.compareTo(MIN_AMOUNT) < 0) {
            throw new IllegalArgumentException("Transaction amount must be at least " + MIN_AMOUNT);
        }
        
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("Transaction amount cannot exceed " + MAX_AMOUNT);
        }
        
        // Check decimal places
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("Transaction amount cannot have more than 2 decimal places");
        }
        
        log.debug("Amount validation passed for: {}", amount);
    }
}