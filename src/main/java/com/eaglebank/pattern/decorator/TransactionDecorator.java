package com.eaglebank.pattern.decorator;

import com.eaglebank.dto.request.CreateTransactionRequest;
import com.eaglebank.dto.response.TransactionResponse;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RequiredArgsConstructor
public abstract class TransactionDecorator implements TransactionProcessor {
    
    protected final TransactionProcessor processor;
    
    @Override
    public TransactionResponse process(UUID userId, UUID accountId, CreateTransactionRequest request) {
        return processor.process(userId, accountId, request);
    }
}