package com.eaglebank.dto.response;

import com.eaglebank.entity.Account.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {
    
    private UUID id;
    private String accountNumber;
    private String accountName;
    private AccountType accountType;
    private BigDecimal balance;
    private BigDecimal creditLimit;
    private BigDecimal availableCredit;
    private UUID userId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}