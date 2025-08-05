package com.eaglebank.dto.request;

import com.eaglebank.entity.Transaction.TransactionType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Request DTO for advanced transaction search with multiple filter criteria.
 */
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Transaction search request with filter criteria")
public class TransactionSearchRequest {
    
    @Schema(description = "Filter by start date (inclusive)", example = "2024-01-01T00:00:00")
    private LocalDateTime startDate;
    
    @Schema(description = "Filter by end date (inclusive)", example = "2024-12-31T23:59:59")
    private LocalDateTime endDate;
    
    @Schema(description = "Filter by minimum amount", example = "100.00")
    @DecimalMin(value = "0.0", inclusive = false, message = "Minimum amount must be positive")
    private BigDecimal minAmount;
    
    @Schema(description = "Filter by maximum amount", example = "10000.00")
    @DecimalMin(value = "0.0", inclusive = false, message = "Maximum amount must be positive")
    private BigDecimal maxAmount;
    
    @Schema(description = "Filter by transaction type", example = "DEPOSIT")
    private TransactionType transactionType;
    
    @Schema(description = "Search by description keyword", example = "salary")
    @Size(max = 100, message = "Description search term must not exceed 100 characters")
    private String descriptionKeyword;
    
    @Schema(description = "Filter by reference number", example = "TXN202401011234560001")
    @Size(max = 50, message = "Reference number must not exceed 50 characters")
    private String referenceNumber;
    
    @Schema(description = "Include only completed transactions", example = "true")
    private Boolean completedOnly;
    
    @Schema(description = "Filter for large transactions above threshold", example = "5000.00")
    @DecimalMin(value = "0.0", inclusive = false, message = "Large transaction threshold must be positive")
    private BigDecimal largeTransactionThreshold;
    
    @AssertTrue(message = "Start date must be before end date")
    @Schema(hidden = true)
    public boolean isDateRangeValid() {
        if (startDate != null && endDate != null) {
            return !startDate.isAfter(endDate);
        }
        return true;
    }
    
    @AssertTrue(message = "Minimum amount must be less than or equal to maximum amount")
    @Schema(hidden = true)
    public boolean isAmountRangeValid() {
        if (minAmount != null && maxAmount != null) {
            return minAmount.compareTo(maxAmount) <= 0;
        }
        return true;
    }
}