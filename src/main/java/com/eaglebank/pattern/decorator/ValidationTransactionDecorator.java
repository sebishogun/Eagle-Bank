package com.eaglebank.pattern.decorator;

import com.eaglebank.dto.request.CreateTransactionRequest;
import com.eaglebank.dto.response.TransactionResponse;
import com.eaglebank.pattern.chain.TransactionValidationChain;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class ValidationTransactionDecorator extends TransactionDecorator {
    
    private final TransactionValidationChain validationChain;
    
    public ValidationTransactionDecorator(TransactionProcessor processor, 
                                        TransactionValidationChain validationChain) {
        super(processor);
        this.validationChain = validationChain;
    }
    
    @Override
    public TransactionResponse process(UUID userId, UUID accountId, CreateTransactionRequest request) {
        log.debug("Validating transaction request");
        
        // Apply validation chain
        validationChain.validate(request);
        
        log.debug("Validation passed, proceeding with transaction");
        return super.process(userId, accountId, request);
    }
}