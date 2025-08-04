package com.eaglebank.pattern.decorator;

import com.eaglebank.dto.request.CreateTransactionRequest;
import com.eaglebank.dto.response.TransactionResponse;

import java.util.UUID;

public interface TransactionProcessor {
    
    TransactionResponse process(UUID userId, UUID accountId, CreateTransactionRequest request);
}