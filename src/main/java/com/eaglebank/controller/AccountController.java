package com.eaglebank.controller;

import com.eaglebank.dto.request.CreateAccountRequest;
import com.eaglebank.dto.request.UpdateAccountRequest;
import com.eaglebank.dto.response.AccountResponse;
import com.eaglebank.security.UserPrincipal;
import com.eaglebank.service.AccountService;
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
@RequestMapping("/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody CreateAccountRequest request) {
        
        log.info("Creating account for user: {}", userPrincipal.getId());
        AccountResponse response = accountService.createAccount(userPrincipal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<AccountResponse>> getUserAccounts(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PageableDefault(size = 20) Pageable pageable) {
        
        log.info("Fetching accounts for user: {}", userPrincipal.getId());
        Page<AccountResponse> accounts = accountService.getUserAccounts(userPrincipal.getId(), pageable);
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccountById(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID accountId) {
        
        log.info("Fetching account {} for user: {}", accountId, userPrincipal.getId());
        AccountResponse response = accountService.getAccountById(userPrincipal.getId(), accountId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{accountId}")
    public ResponseEntity<AccountResponse> updateAccount(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID accountId,
            @Valid @RequestBody UpdateAccountRequest request) {
        
        log.info("Updating account {} for user: {}", accountId, userPrincipal.getId());
        AccountResponse response = accountService.updateAccount(userPrincipal.getId(), accountId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{accountId}")
    public ResponseEntity<Void> deleteAccount(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID accountId) {
        
        log.info("Deleting account {} for user: {}", accountId, userPrincipal.getId());
        accountService.deleteAccount(userPrincipal.getId(), accountId);
        return ResponseEntity.noContent().build();
    }
}