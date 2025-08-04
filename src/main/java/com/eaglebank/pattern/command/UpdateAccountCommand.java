package com.eaglebank.pattern.command;

import com.eaglebank.dto.request.UpdateAccountRequest;
import com.eaglebank.dto.response.AccountResponse;
import com.eaglebank.entity.Account;
import com.eaglebank.repository.AccountRepository;
import com.eaglebank.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class UpdateAccountCommand implements Command<AccountResponse> {
    
    private final AccountService accountService;
    private final AccountRepository accountRepository;
    private final UUID userId;
    private final UUID accountId;
    private final UpdateAccountRequest request;
    private Account.AccountType previousAccountType;
    private AccountResponse updatedAccount;
    
    @Override
    public AccountResponse execute() {
        log.info("Executing UpdateAccountCommand for account: {}", accountId);
        
        // Store previous state for undo
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        previousAccountType = account.getAccountType();
        
        // Execute update
        updatedAccount = accountService.updateAccount(userId, accountId, request);
        return updatedAccount;
    }
    
    @Override
    public void undo() {
        if (!canUndo()) {
            throw new IllegalStateException("Cannot undo: Update was not executed");
        }
        
        log.info("Undoing UpdateAccountCommand - Reverting account type from {} to {}",
                request.getAccountType(), previousAccountType);
        
        // Create reverse update request
        UpdateAccountRequest reverseRequest = new UpdateAccountRequest();
        reverseRequest.setAccountType(previousAccountType);
        
        // Execute reverse update
        accountService.updateAccount(userId, accountId, reverseRequest);
        updatedAccount = null;
    }
    
    @Override
    public boolean canUndo() {
        return updatedAccount != null && previousAccountType != null;
    }
    
    @Override
    public String getDescription() {
        return String.format("Update account %s for user %s", accountId, userId);
    }
}