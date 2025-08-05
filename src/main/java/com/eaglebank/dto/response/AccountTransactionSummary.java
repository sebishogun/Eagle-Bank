package com.eaglebank.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO for account transaction summary with aggregated data.
 * Used for reporting and analytics purposes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Account transaction summary with aggregated metrics")
public class AccountTransactionSummary {
    
    @Schema(description = "Account ID", example = "01234567-89ab-cdef-0123-456789abcdef")
    private UUID accountId;
    
    @Schema(description = "Account number", example = "CHK1234567890")
    private String accountNumber;
    
    @Schema(description = "User ID who owns the account", example = "01234567-89ab-cdef-0123-456789abcdef")
    private UUID userId;
    
    @Schema(description = "Total number of transactions", example = "42")
    private Long transactionCount;
    
    @Schema(description = "Total transaction amount", example = "15000.00")
    private BigDecimal totalAmount;
    
    @Schema(description = "Average transaction amount", example = "357.14")
    private BigDecimal averageAmount;
    
    /**
     * Constructor for JPQL query result mapping.
     * This constructor is used by the @Query annotation in AccountRepository.
     */
    public AccountTransactionSummary(UUID accountId, String accountNumber, UUID userId,
                                   Long transactionCount, BigDecimal totalAmount, Double averageAmount) {
        this.accountId = accountId;
        this.accountNumber = accountNumber;
        this.userId = userId;
        this.transactionCount = transactionCount != null ? transactionCount : 0L;
        this.totalAmount = totalAmount != null ? totalAmount : BigDecimal.ZERO;
        this.averageAmount = averageAmount != null ? BigDecimal.valueOf(averageAmount) : BigDecimal.ZERO;
    }
}