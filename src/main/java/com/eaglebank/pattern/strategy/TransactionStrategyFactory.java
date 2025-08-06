package com.eaglebank.pattern.strategy;

import com.eaglebank.entity.Account;
import com.eaglebank.entity.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TransactionStrategyFactory {
    
    private final List<TransactionStrategy> strategies;
    private final WithdrawalStrategy withdrawalStrategy;
    private final CreditWithdrawalStrategy creditWithdrawalStrategy;
    private final DepositStrategy depositStrategy;
    private final TransferStrategy transferStrategy;
    
    public TransactionStrategy getStrategy(Transaction.TransactionType type) {
        return strategies.stream()
                .filter(strategy -> strategy.canProcess(type))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No strategy found for transaction type: " + type));
    }
    
    public TransactionStrategy getStrategy(Transaction.TransactionType type, Account account) {
        // Special handling for withdrawals based on account type
        if (type == Transaction.TransactionType.WITHDRAWAL) {
            if (account.getAccountType() == Account.AccountType.CREDIT) {
                return creditWithdrawalStrategy;
            } else {
                return withdrawalStrategy;
            }
        }
        
        // Handle transfer type
        if (type == Transaction.TransactionType.TRANSFER) {
            return transferStrategy;
        }
        
        // For other transaction types, use the default strategy
        return getStrategy(type);
    }
}