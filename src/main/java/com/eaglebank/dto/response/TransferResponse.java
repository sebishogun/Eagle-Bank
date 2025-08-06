package com.eaglebank.dto.response;

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
@Schema(description = "Response object containing details of a completed transfer")
public class TransferResponse {
    
    @Schema(
        description = "Unique reference number for the transfer",
        example = "TRF-20250106-123456",
        required = true
    )
    private String transferReference;
    
    @Schema(
        description = "UUID of the source account",
        example = "01234567-89ab-cdef-0123-456789abcdef",
        required = true
    )
    private UUID sourceAccountId;
    
    @Schema(
        description = "UUID of the target account",
        example = "fedcba98-7654-3210-fedc-ba9876543210",
        required = true
    )
    private UUID targetAccountId;
    
    @Schema(
        description = "Amount transferred",
        example = "500.00",
        required = true
    )
    private BigDecimal amount;
    
    @Schema(
        description = "Transfer description if provided",
        example = "Monthly rent payment",
        required = false
    )
    private String description;
    
    @Schema(
        description = "Details of the outgoing transaction from source account",
        required = true
    )
    private TransactionResponse sourceTransaction;
    
    @Schema(
        description = "Details of the incoming transaction to target account",
        required = true
    )
    private TransactionResponse targetTransaction;
    
    @Schema(
        description = "Timestamp when the transfer was completed",
        example = "2025-01-06T14:30:00",
        required = true
    )
    private LocalDateTime timestamp;
    
    @Schema(
        description = "Status of the transfer",
        example = "COMPLETED",
        allowableValues = {"COMPLETED", "PENDING", "FAILED"},
        required = true
    )
    private String status;
}