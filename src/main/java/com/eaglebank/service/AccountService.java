package com.eaglebank.service;

import com.eaglebank.audit.AuditEntry;
import com.eaglebank.audit.Auditable;
import com.eaglebank.dto.request.CreateAccountRequest;
import com.eaglebank.dto.request.UpdateAccountRequest;
import com.eaglebank.dto.response.AccountResponse;
import com.eaglebank.dto.response.AccountTransactionSummary;
import com.eaglebank.entity.Account;
import com.eaglebank.entity.Transaction;
import com.eaglebank.entity.User;
import com.eaglebank.event.AccountCreatedEvent;
import com.eaglebank.exception.ForbiddenException;
import com.eaglebank.exception.ResourceNotFoundException;
import com.eaglebank.metrics.AccountMetricsCollector;
import com.eaglebank.pattern.factory.AccountFactory;
import com.eaglebank.pattern.factory.AccountFactoryProvider;
import com.eaglebank.pattern.observer.EventPublisher;
import com.eaglebank.pattern.specification.AccountSpecifications;
import com.eaglebank.pattern.strategy.AccountStatusStrategy;
import com.eaglebank.pattern.strategy.AccountStatusStrategyFactory;
import com.eaglebank.repository.AccountRepository;
import com.eaglebank.repository.UserRepository;
import com.eaglebank.validation.AccountStatusTransitionValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.eaglebank.config.CacheConfig.ACCOUNTS_CACHE;
import static com.eaglebank.config.CacheConfig.USER_ACCOUNTS_CACHE;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final AccountFactoryProvider factoryProvider;
    private final EventPublisher eventPublisher;
    private final AccountMetricsCollector accountMetricsCollector;
    private final AccountStatusStrategyFactory statusStrategyFactory;
    private final AccountStatusTransitionValidator statusTransitionValidator;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_ACCOUNT_NUMBER_ATTEMPTS = 10;

    @Transactional
    @Auditable(action = AuditEntry.AuditAction.CREATE, entityType = "Account")
    @CacheEvict(value = {USER_ACCOUNTS_CACHE}, key = "#userId")
    public AccountResponse createAccount(UUID userId, CreateAccountRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // Get appropriate factory for account type
        AccountFactory factory = factoryProvider.getFactory(request.getAccountType().name());
        
        // Create account using factory
        Account account = factory.createAccount(user, request.getInitialBalance());
        
        // Set custom account name if provided
        if (request.getAccountName() != null && !request.getAccountName().trim().isEmpty()) {
            account.setAccountName(request.getAccountName().trim());
        }
        
        // Set credit limit if provided (for credit accounts)
        if (request.getCreditLimit() != null && account.getAccountType() == Account.AccountType.CREDIT) {
            account.setCreditLimit(request.getCreditLimit());
        }
        
        // Ensure unique account number
        int attempts = 0;
        while (accountRepository.existsByAccountNumber(account.getAccountNumber()) && attempts < MAX_ACCOUNT_NUMBER_ATTEMPTS) {
            account = factory.createAccount(user, request.getInitialBalance());
            attempts++;
        }
        
        if (attempts >= MAX_ACCOUNT_NUMBER_ATTEMPTS) {
            throw new IllegalStateException("Failed to generate unique account number");
        }

        Account savedAccount = accountRepository.save(account);
        
        // Publish domain event
        AccountCreatedEvent event = new AccountCreatedEvent(
                savedAccount.getId(),
                userId,
                savedAccount.getAccountNumber(),
                savedAccount.getAccountType().name(),
                savedAccount.getBalance()
        );
        eventPublisher.publishEvent(event);
        
        // Record metrics
        accountMetricsCollector.recordAccountCreated(
            savedAccount.getAccountType(),
            savedAccount.getBalance()
        );
        
        log.info("Created {} account {} for user {}", 
                savedAccount.getAccountType(), savedAccount.getAccountNumber(), userId);
        
        return mapToResponse(savedAccount);
    }

    @Cacheable(value = ACCOUNTS_CACHE, key = "#accountId")
    @Auditable(action = AuditEntry.AuditAction.READ, entityType = "Account", entityIdParam = "1")
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

    @Cacheable(value = USER_ACCOUNTS_CACHE, key = "#userId + '_' + (#pageable != null ? #pageable.pageNumber + '_' + #pageable.pageSize : 'all')")
    @Auditable(action = AuditEntry.AuditAction.READ, entityType = "Account")
    public Page<AccountResponse> getUserAccounts(UUID userId, Pageable pageable) {
        Page<Account> accounts = accountRepository.findByUserId(userId, pageable);
        return accounts.map(this::mapToResponse);
    }

    @Transactional
    @Auditable(action = AuditEntry.AuditAction.UPDATE, entityType = "Account", entityIdParam = "1")
    @CacheEvict(value = {ACCOUNTS_CACHE, USER_ACCOUNTS_CACHE}, key = "#accountId")
    public AccountResponse updateAccount(UUID userId, UUID accountId, UpdateAccountRequest request) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + accountId));
        
        validateAccountOwnership(account, userId);
        
        // Check if account status allows updates
        AccountStatusStrategy statusStrategy = statusStrategyFactory.getStrategy(account);
        
        // Handle status change separately
        if (request.getStatus() != null && request.getStatus() != account.getStatus()) {
            // Validate transition
            statusTransitionValidator.validateTransition(account, request.getStatus(), request.getStatusChangeReason());
            
            // Log status change
            log.info("Changing account {} status from {} to {} for reason: {}", 
                    accountId, account.getStatus(), request.getStatus(), request.getStatusChangeReason());
            account.setStatus(request.getStatus());
            
            // Publish status change event
            eventPublisher.publishAccountStatusChanged(account, request.getStatusChangeReason());
        } else if (!statusStrategy.canUpdate(account)) {
            throw new IllegalStateException(statusStrategy.getRestrictionReason());
        }
        
        // Update other fields
        if (request.getAccountName() != null) {
            account.setAccountName(request.getAccountName());
        }
        
        if (request.getAccountType() != null) {
            account.setAccountType(request.getAccountType());
        }
        
        if (request.getCurrency() != null) {
            account.setCurrency(request.getCurrency());
        }
        
        // Credit limit can only be updated for credit accounts
        if (request.getCreditLimit() != null) {
            if (account.getAccountType() == Account.AccountType.CREDIT) {
                account.setCreditLimit(request.getCreditLimit());
            } else {
                log.warn("Attempted to set credit limit on non-credit account {}", accountId);
            }
        }
        
        Account updatedAccount = accountRepository.save(account);
        log.info("Updated account {} for user {}", accountId, userId);
        
        return mapToResponse(updatedAccount);
    }

    @Transactional
    @Auditable(action = AuditEntry.AuditAction.DELETE, entityType = "Account", entityIdParam = "1")
    @CacheEvict(value = {ACCOUNTS_CACHE, USER_ACCOUNTS_CACHE}, key = "#accountId")
    public void deleteAccount(UUID userId, UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + accountId));
        
        validateAccountOwnership(account, userId);
        
        // Check if account status allows deletion
        AccountStatusStrategy statusStrategy = statusStrategyFactory.getStrategy(account);
        if (!statusStrategy.canDelete(account)) {
            throw new IllegalStateException(statusStrategy.getRestrictionReason());
        }
        
        // Check if account has transactions
        long transactionCount = accountRepository.countTransactionsByAccountId(accountId);
        if (transactionCount > 0) {
            throw new IllegalStateException("Cannot delete account with transaction history");
        }
        
        // Record final balance before deletion
        accountMetricsCollector.recordAccountClosed(
            account.getAccountType(),
            account.getBalance()
        );
        
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
        AccountResponse.AccountResponseBuilder builder = AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .accountName(account.getAccountName())
                .accountType(account.getAccountType())
                .status(account.getStatus())
                .balance(account.getBalance())
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
    
    // Transaction-based search methods
    @Transactional(readOnly = true)
    @Auditable(action = AuditEntry.AuditAction.READ, entityType = "Account")
    public List<Account> findHighValueAccounts(BigDecimal threshold) {
        log.debug("Finding accounts with transactions >= {}", threshold);
        return accountRepository.findAccountsByMinimumTransactionAmount(threshold);
    }
    
    @Transactional(readOnly = true)
    @Auditable(action = AuditEntry.AuditAction.READ, entityType = "Account")
    public List<Account> findAccountsWithRecentActivity(UUID userId, LocalDateTime since) {
        log.debug("Finding accounts with activity for user {} since {}", userId, since);
        
        Specification<Account> spec = AccountSpecifications.belongsToUser(userId)
                .and(AccountSpecifications.hasRecentTransactions(since));
        
        return accountRepository.findAll(spec);
    }
    
    @Transactional(readOnly = true)
    @Auditable(action = AuditEntry.AuditAction.READ, entityType = "Account")
    public List<AccountTransactionSummary> getAccountActivitySummary(UUID userId) {
        log.debug("Getting account activity summary for user {}", userId);
        return accountRepository.getAccountTransactionSummariesForUser(userId);
    }
    
    @Transactional(readOnly = true)
    @Auditable(action = AuditEntry.AuditAction.READ, entityType = "Account")
    public List<Account> findDormantAccounts(LocalDateTime lastActivityBefore) {
        log.debug("Finding dormant accounts with no activity since {}", lastActivityBefore);
        
        Specification<Account> spec = AccountSpecifications.activeAccounts()
                .and(Specification.not(AccountSpecifications.hasRecentTransactions(lastActivityBefore)));
        
        return accountRepository.findAll(spec);
    }
    
    @Transactional(readOnly = true)
    @Auditable(action = AuditEntry.AuditAction.READ, entityType = "Account")
    public Page<Account> findAccountsByTransactionCriteria(
            UUID userId,
            BigDecimal minTransactionAmount,
            Transaction.TransactionType transactionType,
            LocalDateTime since,
            Pageable pageable) {
        
        log.debug("Finding accounts for user {} with transaction criteria", userId);
        
        Specification<Account> spec = AccountSpecifications.belongsToUser(userId);
        
        if (minTransactionAmount != null) {
            spec = spec.and(AccountSpecifications.hasHighValueTransaction(minTransactionAmount));
        }
        
        if (transactionType != null) {
            spec = spec.and(AccountSpecifications.hasTransactionOfType(transactionType));
        }
        
        if (since != null) {
            spec = spec.and(AccountSpecifications.hasRecentTransactions(since));
        }
        
        return accountRepository.findAll(spec, pageable);
    }
}