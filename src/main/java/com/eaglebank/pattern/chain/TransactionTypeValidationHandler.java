package com.eaglebank.pattern.chain;

import com.eaglebank.dto.request.CreateTransactionRequest;
import com.eaglebank.entity.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
public class TransactionTypeValidationHandler extends ValidationHandler<CreateTransactionRequest> {
    
    private static final Set<Transaction.TransactionType> ALLOWED_TYPES = Set.of(
            Transaction.TransactionType.DEPOSIT,
            Transaction.TransactionType.WITHDRAWAL
    );
    
    @Override
    protected boolean canHandle(CreateTransactionRequest request) {
        return request.getTransactionType() != null;
    }
    
    @Override
    protected void doValidate(CreateTransactionRequest request) {
        Transaction.TransactionType type = request.getTransactionType();
        
        if (!ALLOWED_TYPES.contains(type)) {
            throw new IllegalArgumentException("Invalid transaction type: " + type);
        }
        
        log.debug("Transaction type validation passed for: {}", type);
    }
}