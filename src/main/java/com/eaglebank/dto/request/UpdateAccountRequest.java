package com.eaglebank.dto.request;

import com.eaglebank.entity.Account.AccountStatus;
import com.eaglebank.entity.Account.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update account details. All fields are optional.")
public class UpdateAccountRequest {
    
    @Schema(description = "New account type", example = "SAVINGS")
    private AccountType accountType;
    
    @Schema(description = "New account name", example = "My Savings Account")
    @Size(min = 1, max = 100, message = "Account name must be between 1 and 100 characters")
    private String accountName;
    
    @Schema(description = "New account status", example = "ACTIVE")
    private AccountStatus status;
    
    @Schema(description = "New credit limit (only for CREDIT accounts)", example = "10000.00")
    @DecimalMin(value = "0.0", inclusive = true, message = "Credit limit cannot be negative")
    private BigDecimal creditLimit;
    
    @Schema(description = "Currency code", example = "USD")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter code")
    private String currency;
    
    @Schema(description = "Reason for status change (required when changing status)", example = "Customer requested account freeze")
    @Size(max = 500, message = "Status change reason cannot exceed 500 characters")
    private String statusChangeReason;
}