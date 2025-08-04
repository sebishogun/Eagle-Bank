package com.eaglebank.event;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class AccountCreatedEvent extends AbstractDomainEvent {
    
    private final UUID accountId;
    private final UUID userId;
    private final String accountNumber;
    private final String accountType;
    private final BigDecimal initialBalance;
    
    public AccountCreatedEvent(UUID accountId, UUID userId, String accountNumber, 
                              String accountType, BigDecimal initialBalance) {
        super();
        this.accountId = accountId;
        this.userId = userId;
        this.accountNumber = accountNumber;
        this.accountType = accountType;
        this.initialBalance = initialBalance;
    }
    
    @Override
    public String getEventType() {
        return "ACCOUNT_CREATED";
    }
}