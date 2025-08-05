package com.eaglebank.pattern.decorator;

import com.eaglebank.dto.request.CreateTransactionRequest;
import com.eaglebank.dto.response.TransactionResponse;
import com.eaglebank.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@RequiredArgsConstructor
public class BaseTransactionProcessor implements TransactionProcessor {
    
    private final TransactionService transactionService;
    
    @Override
    public TransactionResponse process(UUID userId, UUID accountId, CreateTransactionRequest request) {
        return transactionService.createTransaction(userId, accountId, request);
    }
}