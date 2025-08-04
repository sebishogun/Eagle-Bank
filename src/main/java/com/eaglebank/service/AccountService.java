package com.eaglebank.service;

import com.eaglebank.dto.request.CreateAccountRequest;
import com.eaglebank.dto.request.UpdateAccountRequest;
import com.eaglebank.dto.response.AccountResponse;
import com.eaglebank.entity.Account;
import com.eaglebank.entity.User;
import com.eaglebank.exception.ForbiddenException;
import com.eaglebank.exception.ResourceNotFoundException;
import com.eaglebank.repository.AccountRepository;
import com.eaglebank.repository.UserRepository;
import com.eaglebank.util.UuidGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_ACCOUNT_NUMBER_ATTEMPTS = 10;

    @Transactional
    public AccountResponse createAccount(UUID userId, CreateAccountRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Account account = Account.builder()
                .id(UuidGenerator.generateUuidV7())
                .accountNumber(generateUniqueAccountNumber())
                .accountName(user.getFirstName() + " " + user.getLastName() + " " + request.getAccountType())
                .accountType(request.getAccountType())
                .balance(request.getInitialBalance())
                .currency("USD")
                .status(Account.AccountStatus.ACTIVE)
                .user(user)
                .build();

        Account savedAccount = accountRepository.save(account);
        log.info("Created account {} for user {}", savedAccount.getAccountNumber(), userId);
        
        return mapToResponse(savedAccount);
    }

    public AccountResponse getAccountById(UUID userId, UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + accountId));
        
        validateAccountOwnership(account, userId);
        
        return mapToResponse(account);
    }

    public AccountResponse getAccountByAccountNumber(UUID userId, String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with number: " + accountNumber));
        
        validateAccountOwnership(account, userId);
        
        return mapToResponse(account);
    }

    public Page<AccountResponse> getUserAccounts(UUID userId, Pageable pageable) {
        Page<Account> accounts = accountRepository.findByUserId(userId, pageable);
        return accounts.map(this::mapToResponse);
    }

    @Transactional
    public AccountResponse updateAccount(UUID userId, UUID accountId, UpdateAccountRequest request) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + accountId));
        
        validateAccountOwnership(account, userId);
        
        if (request.getAccountType() != null) {
            account.setAccountType(request.getAccountType());
        }
        
        Account updatedAccount = accountRepository.save(account);
        log.info("Updated account {} for user {}", accountId, userId);
        
        return mapToResponse(updatedAccount);
    }

    @Transactional
    public void deleteAccount(UUID userId, UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + accountId));
        
        validateAccountOwnership(account, userId);
        
        // Check if account has transactions
        long transactionCount = accountRepository.countTransactionsByAccountId(accountId);
        if (transactionCount > 0) {
            throw new IllegalStateException("Cannot delete account with transaction history");
        }
        
        accountRepository.delete(account);
        log.info("Deleted account {} for user {}", accountId, userId);
    }

    private void validateAccountOwnership(Account account, UUID userId) {
        if (!account.getUser().getId().equals(userId)) {
            throw new ForbiddenException("User is not authorized to access this account");
        }
    }

    private String generateUniqueAccountNumber() {
        for (int attempt = 0; attempt < MAX_ACCOUNT_NUMBER_ATTEMPTS; attempt++) {
            String accountNumber = generateAccountNumber();
            if (!accountRepository.existsByAccountNumber(accountNumber)) {
                return accountNumber;
            }
        }
        throw new IllegalStateException("Failed to generate unique account number after " + MAX_ACCOUNT_NUMBER_ATTEMPTS + " attempts");
    }

    private String generateAccountNumber() {
        // Generate a 12-digit account number starting with "ACC"
        StringBuilder sb = new StringBuilder("ACC");
        for (int i = 0; i < 10; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private AccountResponse mapToResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .accountType(account.getAccountType())
                .balance(account.getBalance())
                .userId(account.getUser().getId())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}