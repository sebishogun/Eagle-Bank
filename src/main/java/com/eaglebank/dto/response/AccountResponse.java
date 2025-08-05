package com.eaglebank.dto.response;

import com.eaglebank.entity.Account.AccountType;
import com.eaglebank.entity.Account.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Account details response")
public class AccountResponse {
    
    @Schema(description = "Account ID", example = "550e8400-e29b-41d4-a716-446655440001")
    private UUID id;
    
    @Schema(description = "Account number", example = "ACC1234567890")
    private String accountNumber;
    
    @Schema(description = "Account name", example = "Primary Checking")
    private String accountName;
    
    @Schema(description = "Type of account", example = "CHECKING")
    private AccountType accountType;
    
    @Schema(description = "Account status", example = "ACTIVE")
    private AccountStatus status;
    
    @Schema(description = "Current balance", example = "2500.00")
    private BigDecimal balance;
    
    @Schema(description = "Currency code", example = "USD")
    private String currency;
    
    @Schema(description = "Credit limit (only for CREDIT accounts)", example = "5000.00", nullable = true)
    private BigDecimal creditLimit;
    
    @Schema(description = "Available credit (only for CREDIT accounts)", example = "3500.00", nullable = true)
    private BigDecimal availableCredit;
    
    @Schema(description = "User ID who owns the account", example = "550e8400-e29b-41d4-a716-446655440002")
    private UUID userId;
    
    @Schema(description = "Account creation timestamp", example = "2024-01-15T10:30:00")
    private LocalDateTime createdAt;
    
    @Schema(description = "Last update timestamp", example = "2024-01-20T14:45:00")
    private LocalDateTime updatedAt;
}