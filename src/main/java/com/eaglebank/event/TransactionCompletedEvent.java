package com.eaglebank.event;

import com.eaglebank.entity.Transaction;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class TransactionCompletedEvent extends AbstractDomainEvent {
    
    private final UUID transactionId;
    private final UUID accountId;
    private final UUID userId;
    private final String referenceNumber;
    private final Transaction.TransactionType type;
    private final BigDecimal amount;
    private final BigDecimal balanceAfter;
    
    public TransactionCompletedEvent(UUID transactionId, UUID accountId, UUID userId,
                                   String referenceNumber, Transaction.TransactionType type,
                                   BigDecimal amount, BigDecimal balanceAfter) {
        super();
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.userId = userId;
        this.referenceNumber = referenceNumber;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
    }
    
    @Override
    public String getEventType() {
        return "TRANSACTION_COMPLETED";
    }
}