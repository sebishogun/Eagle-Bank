package com.eaglebank.controller;

import com.eaglebank.dto.request.CreateTransactionRequest;
import com.eaglebank.dto.request.TransactionSearchRequest;
import com.eaglebank.dto.response.TransactionResponse;
import com.eaglebank.entity.Transaction;
import com.eaglebank.exception.ErrorResponse;
import com.eaglebank.security.UserPrincipal;
import com.eaglebank.service.TransactionService;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/v1/accounts/{accountId}/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Transaction management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    @Operation(summary = "Create transaction",
              description = "Creates a new transaction (deposit or withdrawal) for an account. Withdrawals require sufficient funds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Transaction created successfully",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = TransactionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid transaction data",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - attempting transaction on another user's account",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Insufficient funds for withdrawal",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TransactionResponse> createTransaction(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "Account ID", required = true, example = "550e8400-e29b-41d4-a716-446655440001")
            @PathVariable UUID accountId,
            @Parameter(description = "Transaction details", required = true)
            @Valid @RequestBody CreateTransactionRequest request) {
        
        log.info("Creating {} transaction for account: {} by user: {}", 
                request.getTransactionType(), accountId, userPrincipal.getId());
        
        TransactionResponse response = transactionService.createTransaction(
                userPrincipal.getId(), accountId, request);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List account transactions",
              description = "Retrieves a paginated list of transactions for an account with optional filtering. Users can only view transactions for their own accounts.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transactions retrieved successfully",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - viewing another user's transactions",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Page<TransactionResponse>> getAccountTransactions(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "Account ID", required = true, example = "550e8400-e29b-41d4-a716-446655440001")
            @PathVariable UUID accountId,
            @Parameter(description = "Filter by start date (inclusive)", example = "2024-01-01T00:00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "Filter by end date (inclusive)", example = "2024-12-31T23:59:59")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @Parameter(description = "Filter by minimum amount", example = "100.00")
            @RequestParam(required = false) BigDecimal minAmount,
            @Parameter(description = "Filter by maximum amount", example = "1000.00")
            @RequestParam(required = false) BigDecimal maxAmount,
            @Parameter(description = "Filter by transaction type", schema = @Schema(allowableValues = {"DEPOSIT", "WITHDRAWAL"}))
            @RequestParam(required = false) Transaction.TransactionType type,
            @Parameter(description = "Search by description keyword", example = "salary")
            @RequestParam(required = false) String description,
            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        
        log.info("Fetching transactions for account: {} by user: {} with filters", 
                accountId, userPrincipal.getId());
        
        Page<TransactionResponse> transactions = transactionService.searchAccountTransactions(
                userPrincipal.getId(), accountId, startDate, endDate, minAmount, maxAmount, type, description, pageable);
        
        return ResponseEntity.ok(transactions);
    }
    
    @PostMapping("/search")
    @Operation(summary = "Search transactions with advanced filters",
              description = "Performs advanced search on account transactions using multiple filter criteria. Users can only search transactions for their own accounts.")
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
            @ApiResponse(responseCode = "403", description = "Forbidden - searching another user's transactions",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Page<TransactionResponse>> searchTransactions(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "Account ID", required = true, example = "550e8400-e29b-41d4-a716-446655440001")
            @PathVariable UUID accountId,
            @Parameter(description = "Search criteria", required = true)
            @Valid @RequestBody TransactionSearchRequest searchRequest,
            @Parameter(description = "Pagination parameters")
            @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        
        log.info("Searching transactions for account: {} by user: {} with criteria", 
                accountId, userPrincipal.getId());
        
        Page<TransactionResponse> transactions = transactionService.advancedSearchTransactions(
                userPrincipal.getId(), accountId, searchRequest, pageable);
        
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Get transaction by ID",
              description = "Retrieves a specific transaction by its ID. Users can only view transactions for their own accounts.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction found",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = TransactionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - viewing another user's transaction",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Transaction or account not found",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TransactionResponse> getTransactionById(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "Account ID", required = true, example = "550e8400-e29b-41d4-a716-446655440001")
            @PathVariable UUID accountId,
            @Parameter(description = "Transaction ID", required = true, example = "550e8400-e29b-41d4-a716-446655440002")
            @PathVariable UUID transactionId) {
        
        log.info("Fetching transaction {} for account: {} by user: {}", 
                transactionId, accountId, userPrincipal.getId());
        
        TransactionResponse response = transactionService.getTransactionById(
                userPrincipal.getId(), accountId, transactionId);
        
        return ResponseEntity.ok(response);
    }
}