package com.eaglebank.pattern.strategy;

import com.eaglebank.entity.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TransactionStrategyFactory {
    
    private final List<TransactionStrategy> strategies;
    
    public TransactionStrategy getStrategy(Transaction.TransactionType type) {
        return strategies.stream()
                .filter(strategy -> strategy.canProcess(type))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No strategy found for transaction type: " + type));
    }
}