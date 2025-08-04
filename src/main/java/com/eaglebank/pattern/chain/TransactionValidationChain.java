package com.eaglebank.pattern.chain;

import com.eaglebank.dto.request.CreateTransactionRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TransactionValidationChain {
    
    private final ValidationHandler<CreateTransactionRequest> chain;
    
    public TransactionValidationChain(AmountValidationHandler amountHandler,
                                    TransactionTypeValidationHandler typeHandler,
                                    DescriptionValidationHandler descriptionHandler) {
        // Build the chain
        this.chain = amountHandler;
        amountHandler.setNext(typeHandler)
                     .setNext(descriptionHandler);
        
        log.info("Transaction validation chain initialized");
    }
    
    public void validate(CreateTransactionRequest request) {
        log.debug("Starting transaction validation for amount: {}", request.getAmount());
        chain.validate(request);
        log.debug("Transaction validation completed successfully");
    }
}