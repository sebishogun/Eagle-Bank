package com.eaglebank.controller;

import com.eaglebank.dto.response.AccountResponse;
import com.eaglebank.entity.Account;
import com.eaglebank.exception.ErrorResponse;
import com.eaglebank.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin controller for account management operations.
 * All endpoints require ADMIN role.
 */
@Slf4j
@RestController
@RequestMapping("/v1/admin/accounts")
@RequiredArgsConstructor
@Validated
@Tag(name = "Admin Account Management", description = "Administrative endpoints for account management")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAccountController {

    private final AccountService accountService;

    @GetMapping("/high-value")
    @Operation(summary = "Find high-value accounts",
              description = "Retrieves all accounts that have transactions above the specified threshold. Requires ADMIN role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Accounts retrieved successfully",
                    content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = AccountResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Invalid threshold value",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<AccountResponse>> findHighValueAccounts(
            @Parameter(description = "Minimum transaction amount threshold", required = true, example = "10000.00")
            @RequestParam @Positive(message = "Threshold must be positive") BigDecimal threshold) {
        
        log.info("Admin searching for accounts with transactions >= {}", threshold);
        List<Account> accounts = accountService.findHighValueAccounts(threshold);
        List<AccountResponse> responses = accounts.stream()
                .map(this::mapToResponse)
                .toList();
        
        log.info("Found {} high-value accounts", responses.size());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/dormant")
    @Operation(summary = "Find dormant accounts",
              description = "Retrieves all active accounts that have had no transactions since the specified date. Requires ADMIN role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Dormant accounts retrieved successfully",
                    content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = AccountResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Invalid date format",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<AccountResponse>> findDormantAccounts(
            @Parameter(description = "Date to check for last activity", required = true, example = "2023-01-01T00:00:00")
            @RequestParam LocalDateTime inactiveSince) {
        
        log.info("Admin searching for accounts dormant since {}", inactiveSince);
        List<Account> accounts = accountService.findDormantAccounts(inactiveSince);
        List<AccountResponse> responses = accounts.stream()
                .map(this::mapToResponse)
                .toList();
        
        log.info("Found {} dormant accounts", responses.size());
        return ResponseEntity.ok(responses);
    }

    private AccountResponse mapToResponse(Account account) {
        AccountResponse.AccountResponseBuilder builder = AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .accountName(account.getAccountName())
                .accountType(account.getAccountType())
                .status(account.getStatus())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .userId(account.getUser().getId())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt());
        
        // Add credit-specific fields for credit accounts
        if (account.getAccountType() == Account.AccountType.CREDIT) {
            builder.creditLimit(account.getCreditLimit());
            // Calculate available credit: creditLimit + balance (balance is negative for credit used)
            java.math.BigDecimal availableCredit = account.getCreditLimit() != null 
                ? account.getCreditLimit().add(account.getBalance())
                : java.math.BigDecimal.ZERO;
            builder.availableCredit(availableCredit);
        }
        
        return builder.build();
    }
}