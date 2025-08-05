package com.eaglebank.controller;

import com.eaglebank.dto.request.AccountSearchRequest;
import com.eaglebank.dto.request.CreateAccountRequest;
import com.eaglebank.dto.request.UpdateAccountRequest;
import com.eaglebank.dto.response.AccountResponse;
import com.eaglebank.dto.response.AccountTransactionSummary;
import com.eaglebank.entity.Account;
import com.eaglebank.exception.ErrorResponse;
import com.eaglebank.security.UserPrincipal;
import com.eaglebank.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Bank account management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    @Operation(summary = "Create a bank account",
              description = "Creates a new bank account for the authenticated user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Account created successfully",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AccountResponse> createAccount(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "Account creation request", required = true)
            @Valid @RequestBody CreateAccountRequest request) {
        
        log.info("Creating account for user: {}", userPrincipal.getId());
        AccountResponse response = accountService.createAccount(userPrincipal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List user's accounts",
              description = "Retrieves a paginated list of all bank accounts for the authenticated user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Accounts retrieved successfully",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Page<AccountResponse>> getUserAccounts(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 20) Pageable pageable) {
        
        log.info("Fetching accounts for user: {}", userPrincipal.getId());
        Page<AccountResponse> accounts = accountService.getUserAccounts(userPrincipal.getId(), pageable);
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/{accountId}")
    @Operation(summary = "Get account details",
              description = "Retrieves details of a specific bank account. Users can only access their own accounts.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account found",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - accessing another user's account",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AccountResponse> getAccountById(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "Account ID", required = true, example = "550e8400-e29b-41d4-a716-446655440001")
            @PathVariable UUID accountId) {
        
        log.info("Fetching account {} for user: {}", accountId, userPrincipal.getId());
        AccountResponse response = accountService.getAccountById(userPrincipal.getId(), accountId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{accountId}")
    @Operation(summary = "Update account",
              description = "Updates account information. Users can only update their own accounts. All fields are optional.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account updated successfully",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - updating another user's account",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AccountResponse> updateAccount(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "Account ID", required = true, example = "550e8400-e29b-41d4-a716-446655440001")
            @PathVariable UUID accountId,
            @Parameter(description = "Account update request", required = true)
            @Valid @RequestBody UpdateAccountRequest request) {
        
        log.info("Updating account {} for user: {}", accountId, userPrincipal.getId());
        AccountResponse response = accountService.updateAccount(userPrincipal.getId(), accountId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{accountId}")
    @Operation(summary = "Delete account",
              description = "Deletes a bank account. Users can only delete their own accounts. Account must have zero balance.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Account deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - deleting another user's account",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflict - account has non-zero balance",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteAccount(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "Account ID", required = true, example = "550e8400-e29b-41d4-a716-446655440001")
            @PathVariable UUID accountId) {
        
        log.info("Deleting account {} for user: {}", accountId, userPrincipal.getId());
        accountService.deleteAccount(userPrincipal.getId(), accountId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/activity-summary")
    @Operation(summary = "Get account activity summary",
              description = "Retrieves transaction summaries for all accounts owned by the authenticated user, including transaction counts and average amounts")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Activity summaries retrieved successfully",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AccountTransactionSummary.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<AccountTransactionSummary>> getAccountActivitySummary(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        log.info("Getting account activity summary for user: {}", userPrincipal.getId());
        List<AccountTransactionSummary> summaries = accountService.getAccountActivitySummary(userPrincipal.getId());
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/recent-activity")
    @Operation(summary = "Get accounts with recent activity",
              description = "Retrieves accounts that have had transactions since the specified date")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Accounts retrieved successfully",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid date format",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<AccountResponse>> getAccountsWithRecentActivity(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "Date to check activity since", required = true, example = "2024-01-01T00:00:00")
            @RequestParam LocalDateTime since) {
        
        log.info("Getting accounts with activity since {} for user: {}", since, userPrincipal.getId());
        List<Account> accounts = accountService.findAccountsWithRecentActivity(userPrincipal.getId(), since);
        List<AccountResponse> responses = accounts.stream()
                .map(this::mapToResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/search")
    @Operation(summary = "Search accounts by transaction criteria",
              description = "Searches user's accounts based on complex transaction criteria. All search parameters are optional.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search completed successfully",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "400", description = "Invalid search criteria",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Page<AccountResponse>> searchAccounts(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "Search criteria", required = true)
            @Valid @RequestBody AccountSearchRequest searchRequest,
            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 20) Pageable pageable) {
        
        log.info("Searching accounts for user: {} with criteria: {}", userPrincipal.getId(), searchRequest);
        Page<Account> accounts = accountService.findAccountsByTransactionCriteria(
                userPrincipal.getId(),
                searchRequest.getMinTransactionAmount(),
                searchRequest.getTransactionType(),
                searchRequest.getSince(),
                pageable
        );
        
        Page<AccountResponse> responses = accounts.map(this::mapToResponse);
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