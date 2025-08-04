package com.eaglebank.controller;

import com.eaglebank.dto.request.CreateTransactionRequest;
import com.eaglebank.dto.response.TransactionResponse;
import com.eaglebank.security.UserPrincipal;
import com.eaglebank.service.TransactionService;
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

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/v1/accounts/{accountId}/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID accountId,
            @Valid @RequestBody CreateTransactionRequest request) {
        
        log.info("Creating {} transaction for account: {} by user: {}", 
                request.getTransactionType(), accountId, userPrincipal.getId());
        
        TransactionResponse response = transactionService.createTransaction(
                userPrincipal.getId(), accountId, request);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<TransactionResponse>> getAccountTransactions(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID accountId,
            @PageableDefault(size = 20, sort = "transactionDate", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        
        log.info("Fetching transactions for account: {} by user: {}", 
                accountId, userPrincipal.getId());
        
        Page<TransactionResponse> transactions = transactionService.getAccountTransactions(
                userPrincipal.getId(), accountId, pageable);
        
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> getTransactionById(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID accountId,
            @PathVariable UUID transactionId) {
        
        log.info("Fetching transaction {} for account: {} by user: {}", 
                transactionId, accountId, userPrincipal.getId());
        
        TransactionResponse response = transactionService.getTransactionById(
                userPrincipal.getId(), accountId, transactionId);
        
        return ResponseEntity.ok(response);
    }
}