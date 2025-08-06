package com.eaglebank.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Digits;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request object for creating a money transfer between accounts")
public class CreateTransferRequest {
    
    @NotNull(message = "Source account ID is required")
    @Schema(
        description = "UUID of the source account to transfer from. User must own this account",
        example = "01234567-89ab-cdef-0123-456789abcdef",
        required = true
    )
    private UUID sourceAccountId;
    
    @NotNull(message = "Target account ID is required")
    @Schema(
        description = "UUID of the target account to transfer to. Can be owned by any user",
        example = "fedcba98-7654-3210-fedc-ba9876543210",
        required = true
    )
    private UUID targetAccountId;
    
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    @Digits(integer = 10, fraction = 2, message = "Amount must have at most 10 integer digits and 2 decimal places")
    @Schema(
        description = "Amount to transfer. Must be positive and within available balance",
        example = "500.00",
        minimum = "0.01",
        maximum = "9999999999.99",
        required = true
    )
    private BigDecimal amount;
    
    @Size(max = 500, message = "Description must not exceed 500 characters")
    @Schema(
        description = "Optional description for the transfer",
        example = "Monthly rent payment",
        maxLength = 500,
        required = false
    )
    private String description;
}