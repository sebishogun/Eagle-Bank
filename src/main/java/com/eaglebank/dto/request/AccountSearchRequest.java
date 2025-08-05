package com.eaglebank.dto.request;

import com.eaglebank.entity.Transaction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Request DTO for searching accounts based on transaction criteria.
 * All fields are optional - if not provided, the filter is not applied.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Search criteria for filtering accounts based on transaction patterns")
public class AccountSearchRequest {
    
    @Positive(message = "Minimum transaction amount must be positive")
    @Schema(description = "Minimum transaction amount threshold", 
            example = "1000.00",
            nullable = true)
    private BigDecimal minTransactionAmount;
    
    @Schema(description = "Type of transactions to filter by", 
            example = "DEPOSIT",
            nullable = true,
            allowableValues = {"DEPOSIT", "WITHDRAWAL"})
    private Transaction.TransactionType transactionType;
    
    @PastOrPresent(message = "Date must not be in the future")
    @Schema(description = "Filter transactions since this date", 
            example = "2024-01-01T00:00:00",
            nullable = true)
    private LocalDateTime since;
    
    @Schema(description = "Include only accounts with recent activity", 
            example = "true",
            nullable = true,
            defaultValue = "false")
    private Boolean activeOnly;
}