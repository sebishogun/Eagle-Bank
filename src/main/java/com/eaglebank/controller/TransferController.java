package com.eaglebank.controller;

import com.eaglebank.dto.request.CreateTransferRequest;
import com.eaglebank.dto.response.TransferResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1/transfers")
@RequiredArgsConstructor
@Tag(name = "Transfers", description = "Money transfer endpoints")
@SecurityRequirement(name = "bearerAuth")
public class TransferController {

    private final TransactionService transactionService;

    @PostMapping
    @Operation(summary = "Create transfer",
              description = "Transfers money from one account to another. User must own the source account. Both accounts must be eligible for transfers based on their status.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Transfer completed successfully",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = TransferResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid transfer data or same source and target account",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid token",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - user does not own source account",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Source or target account not found",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Insufficient funds or account status prevents transfer",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Too many requests",
                    content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TransferResponse> createTransfer(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "Transfer details", required = true)
            @Valid @RequestBody CreateTransferRequest request) {
        
        log.info("Creating transfer from account {} to account {} for amount {} by user {}", 
                request.getSourceAccountId(), request.getTargetAccountId(), 
                request.getAmount(), userPrincipal.getId());
        
        TransferResponse response = transactionService.createTransfer(userPrincipal.getId(), request);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}