package com.eaglebank.pattern.command;

import com.eaglebank.dto.request.CreateAccountRequest;
import com.eaglebank.dto.response.AccountResponse;
import com.eaglebank.repository.AccountRepository;
import com.eaglebank.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class CreateAccountCommand implements Command<AccountResponse> {
    
    private final AccountService accountService;
    private final AccountRepository accountRepository;
    private final UUID userId;
    private final CreateAccountRequest request;
    private AccountResponse createdAccount;
    
    @Override
    public AccountResponse execute() {
        log.info("Executing CreateAccountCommand for user: {}", userId);
        createdAccount = accountService.createAccount(userId, request);
        return createdAccount;
    }
    
    @Override
    public void undo() {
        if (!canUndo()) {
            throw new IllegalStateException("Cannot undo: Account was not created or already has transactions");
        }
        
        log.info("Undoing CreateAccountCommand - Deleting account: {}", createdAccount.getAccountNumber());
        
        // Check if account has transactions
        long transactionCount = accountRepository.countTransactionsByAccountId(createdAccount.getId());
        if (transactionCount > 0) {
            throw new IllegalStateException("Cannot undo: Account has transactions");
        }
        
        // Delete the account
        accountRepository.deleteById(createdAccount.getId());
        createdAccount = null;
    }
    
    @Override
    public boolean canUndo() {
        return createdAccount != null;
    }
    
    @Override
    public String getDescription() {
        return String.format("Create %s account for user %s", request.getAccountType(), userId);
    }
}