package com.eaglebank.service;

import com.eaglebank.audit.Auditable;
import com.eaglebank.audit.AuditEntry;
import com.eaglebank.dto.request.CreateTransactionRequest;
import com.eaglebank.dto.request.CreateTransferRequest;
import com.eaglebank.dto.request.TransactionSearchRequest;
import com.eaglebank.dto.response.TransactionResponse;
import com.eaglebank.dto.response.TransferResponse;
import com.eaglebank.entity.Account;
import com.eaglebank.entity.Transaction;
import com.eaglebank.entity.Transaction.TransactionStatus;
import com.eaglebank.entity.Transaction.TransactionType;
import com.eaglebank.pattern.observer.EventPublisher;
import com.eaglebank.event.TransactionCompletedEvent;
import com.eaglebank.exception.ForbiddenException;
import com.eaglebank.exception.InsufficientFundsException;
import com.eaglebank.exception.ResourceNotFoundException;
import com.eaglebank.pattern.strategy.AccountStatusStrategy;
import com.eaglebank.pattern.strategy.AccountStatusStrategyFactory;
import com.eaglebank.pattern.specification.TransactionSpecifications;
import org.springframework.data.jpa.domain.Specification;
import com.eaglebank.pattern.strategy.TransactionStrategy;
import com.eaglebank.pattern.strategy.TransactionStrategyFactory;
import com.eaglebank.repository.AccountRepository;
import com.eaglebank.repository.TransactionRepository;
import com.eaglebank.util.UuidGenerator;
import com.eaglebank.metrics.TransactionMetricsCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static com.eaglebank.config.CacheConfig.ACCOUNTS_CACHE;
import static com.eaglebank.config.CacheConfig.ACCOUNT_TRANSACTIONS_CACHE;
import static com.eaglebank.config.CacheConfig.TRANSACTIONS_CACHE;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final TransactionStrategyFactory strategyFactory;
    private final AccountStatusStrategyFactory statusStrategyFactory;
    private final EventPublisher eventPublisher;
    private final TransactionMetricsCollector metricsCollector;
    private static final DateTimeFormatter REFERENCE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Transactional
    @Auditable(action = AuditEntry.AuditAction.CREATE, entityType = "Transaction", entityIdParam = "1")
    @CacheEvict(value = {ACCOUNTS_CACHE, ACCOUNT_TRANSACTIONS_CACHE}, key = "#accountId")
    public TransactionResponse createTransaction(UUID userId, UUID accountId, CreateTransactionRequest request) {
        // Validate amount first
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        
        // Find and validate account with pessimistic lock to prevent concurrent modifications
        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + accountId));
        
        // Check authorization
        validateAccountOwnership(account, userId);
        
        // Check account status
        AccountStatusStrategy statusStrategy = statusStrategyFactory.getStrategy(account);
        if (request.getTransactionType() == TransactionType.WITHDRAWAL && !statusStrategy.canWithdraw(account, request.getAmount())) {
            throw new IllegalStateException(statusStrategy.getRestrictionReason());
        } else if (request.getTransactionType() == TransactionType.DEPOSIT && !statusStrategy.canDeposit(account, request.getAmount())) {
            throw new IllegalStateException(statusStrategy.getRestrictionReason());
        }
        
        // Get strategy for transaction type and account type
        TransactionStrategy strategy = strategyFactory.getStrategy(request.getTransactionType(), account);
        
        // Validate transaction using strategy
        strategy.validateTransaction(account, request.getAmount());
        
        // Calculate new balance using strategy
        BigDecimal balanceAfter = strategy.calculateNewBalance(account, request.getAmount());

        // Create transaction
        Transaction transaction = Transaction.builder()
                .id(UuidGenerator.generateUuidV7())
                .referenceNumber(generateTransactionReference())
                .type(request.getTransactionType())
                .amount(request.getAmount())
                .balanceAfter(balanceAfter)
                .description(request.getDescription() != null ? request.getDescription() : strategy.getTransactionDescription(request.getAmount()))
                .status(TransactionStatus.COMPLETED)
                .account(account)
                .transactionDate(LocalDateTime.now())
                .build();
        
        // Update account balance
        account.setBalance(balanceAfter);
        
        // Save transaction and account
        Transaction savedTransaction = transactionRepository.save(transaction);
        accountRepository.save(account);
        
        // Execute post-processing using strategy
        strategy.postProcess(savedTransaction);
        
        // Publish domain event
        TransactionCompletedEvent event = new TransactionCompletedEvent(
                savedTransaction.getId(),
                account.getId(),
                account.getUser().getId(),
                savedTransaction.getReferenceNumber(),
                savedTransaction.getType(),
                savedTransaction.getAmount(),
                savedTransaction.getBalanceAfter()
        );
        eventPublisher.publishEvent(event);
        
        // Record metrics
        metricsCollector.recordTransaction(
            savedTransaction.getType(),
            savedTransaction.getAmount(),
            0L // Processing time would need to be calculated
        );
        
        log.info("Created {} transaction {} for account {} with amount {}", 
                request.getTransactionType(), savedTransaction.getReferenceNumber(), 
                accountId, request.getAmount());
        
        return mapToResponse(savedTransaction);
    }


    @Transactional(readOnly = true)
    @Cacheable(value = TRANSACTIONS_CACHE, key = "#accountId + '_' + #transactionId")
    @Auditable(action = AuditEntry.AuditAction.READ, entityType = "Transaction", entityIdParam = "2")
    public TransactionResponse getTransactionById(UUID userId, UUID accountId, UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + transactionId));
        
        // Verify transaction belongs to the specified account
        if (!transaction.getAccount().getId().equals(accountId)) {
            throw new ResourceNotFoundException("Transaction not found for this account");
        }
        
        // Check authorization
        validateTransactionOwnership(transaction, userId);
        
        return mapToResponse(transaction);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = ACCOUNT_TRANSACTIONS_CACHE, key = "#accountId + '_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    @Auditable(action = AuditEntry.AuditAction.READ, entityType = "Transaction")
    public Page<TransactionResponse> getAccountTransactions(UUID userId, UUID accountId, Pageable pageable) {
        // Find and validate account
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + accountId));
        
        // Check authorization
        validateAccountOwnership(account, userId);
        
        // Get transactions for the account
        Page<Transaction> transactions = transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable);
        
        return transactions.map(this::mapToResponse);
    }

    private void validateAccountOwnership(Account account, UUID userId) {
        if (!account.getUser().getId().equals(userId)) {
            throw new ForbiddenException("User is not authorized to access this account");
        }
    }

    private void validateTransactionOwnership(Transaction transaction, UUID userId) {
        if (!transaction.getAccount().getUser().getId().equals(userId)) {
            throw new ForbiddenException("User is not authorized to access this transaction");
        }
    }

    private String generateTransactionReference() {
        // Generate a unique transaction reference
        // Format: TXN + timestamp + random 4 digits
        String timestamp = LocalDateTime.now().format(REFERENCE_FORMATTER);
        int random = (int) (Math.random() * 10000);
        return String.format("TXN%s%04d", timestamp, random);
    }

    private TransactionResponse mapToResponse(Transaction transaction) {
        // Calculate balance before from balance after and amount
        BigDecimal balanceBefore;
        if (transaction.getType() == TransactionType.DEPOSIT) {
            balanceBefore = transaction.getBalanceAfter().subtract(transaction.getAmount());
        } else {
            balanceBefore = transaction.getBalanceAfter().add(transaction.getAmount());
        }
        
        return TransactionResponse.builder()
                .id(transaction.getId())
                .transactionReference(transaction.getReferenceNumber())
                .transactionType(transaction.getType())
                .amount(transaction.getAmount())
                .balanceBefore(balanceBefore)
                .balanceAfter(transaction.getBalanceAfter())
                .description(transaction.getDescription())
                .status(transaction.getStatus())
                .accountId(transaction.getAccount().getId())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .build();
    }
    
    @Transactional(readOnly = true)
    @Auditable(action = AuditEntry.AuditAction.READ, entityType = "Transaction")
    public Page<TransactionResponse> searchAccountTransactions(UUID userId, UUID accountId,
                                                              LocalDateTime startDate, LocalDateTime endDate,
                                                              BigDecimal minAmount, BigDecimal maxAmount,
                                                              TransactionType type, String description,
                                                              Pageable pageable) {
        // Find and validate account
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + accountId));
        
        // Check authorization
        validateAccountOwnership(account, userId);
        
        // Build specification based on filters
        Specification<Transaction> spec = TransactionSpecifications.forAccount(accountId);
        
        // Add date filters
        if (startDate != null && endDate != null) {
            spec = spec.and(TransactionSpecifications.transactedBetween(startDate, endDate));
        } else if (startDate != null) {
            spec = spec.and(TransactionSpecifications.transactedAfter(startDate));
        } else if (endDate != null) {
            spec = spec.and(TransactionSpecifications.transactedBefore(endDate));
        }
        
        // Add amount filters
        if (minAmount != null && maxAmount != null) {
            spec = spec.and(TransactionSpecifications.amountBetween(minAmount, maxAmount));
        } else if (minAmount != null) {
            spec = spec.and(TransactionSpecifications.amountGreaterThan(minAmount));
        } else if (maxAmount != null) {
            spec = spec.and(TransactionSpecifications.amountLessThan(maxAmount));
        }
        
        // Add type filter
        if (type != null) {
            spec = spec.and(TransactionSpecifications.ofType(type));
        }
        
        // Add description filter
        if (description != null && !description.trim().isEmpty()) {
            spec = spec.and(TransactionSpecifications.descriptionContains(description.trim()));
        }
        
        // Execute search
        Page<Transaction> transactions = transactionRepository.findAll(spec, pageable);
        
        log.info("Found {} transactions matching search criteria for account {}", 
                transactions.getTotalElements(), accountId);
        
        return transactions.map(this::mapToResponse);
    }
    
    @Transactional(readOnly = true)
    @Auditable(action = AuditEntry.AuditAction.READ, entityType = "Transaction")
    public Page<TransactionResponse> advancedSearchTransactions(UUID userId, UUID accountId,
                                                               TransactionSearchRequest searchRequest,
                                                               Pageable pageable) {
        // Find and validate account
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + accountId));
        
        // Check authorization
        validateAccountOwnership(account, userId);
        
        // Build specification based on search request
        Specification<Transaction> spec = TransactionSpecifications.forAccount(accountId);
        
        // Add date filters
        if (searchRequest.getStartDate() != null && searchRequest.getEndDate() != null) {
            spec = spec.and(TransactionSpecifications.transactedBetween(
                    searchRequest.getStartDate(), searchRequest.getEndDate()));
        } else if (searchRequest.getStartDate() != null) {
            spec = spec.and(TransactionSpecifications.transactedAfter(searchRequest.getStartDate()));
        } else if (searchRequest.getEndDate() != null) {
            spec = spec.and(TransactionSpecifications.transactedBefore(searchRequest.getEndDate()));
        }
        
        // Add amount filters
        if (searchRequest.getMinAmount() != null && searchRequest.getMaxAmount() != null) {
            spec = spec.and(TransactionSpecifications.amountBetween(
                    searchRequest.getMinAmount(), searchRequest.getMaxAmount()));
        } else if (searchRequest.getMinAmount() != null) {
            spec = spec.and(TransactionSpecifications.amountGreaterThan(searchRequest.getMinAmount()));
        } else if (searchRequest.getMaxAmount() != null) {
            spec = spec.and(TransactionSpecifications.amountLessThan(searchRequest.getMaxAmount()));
        }
        
        // Add type filter
        if (searchRequest.getTransactionType() != null) {
            spec = spec.and(TransactionSpecifications.ofType(searchRequest.getTransactionType()));
        }
        
        // Add description filter
        if (searchRequest.getDescriptionKeyword() != null && 
            !searchRequest.getDescriptionKeyword().trim().isEmpty()) {
            spec = spec.and(TransactionSpecifications.descriptionContains(
                    searchRequest.getDescriptionKeyword().trim()));
        }
        
        // Add reference number filter
        if (searchRequest.getReferenceNumber() != null && 
            !searchRequest.getReferenceNumber().trim().isEmpty()) {
            spec = spec.and(TransactionSpecifications.referenceNumberEquals(
                    searchRequest.getReferenceNumber().trim()));
        }
        
        // Add completed only filter
        if (Boolean.TRUE.equals(searchRequest.getCompletedOnly())) {
            spec = spec.and(TransactionSpecifications.completedTransactions());
        }
        
        // Add large transaction filter
        if (searchRequest.getLargeTransactionThreshold() != null) {
            spec = spec.and(TransactionSpecifications.largeTransactions(
                    searchRequest.getLargeTransactionThreshold()));
        }
        
        // Execute search
        Page<Transaction> transactions = transactionRepository.findAll(spec, pageable);
        
        log.info("Advanced search found {} transactions matching criteria for account {}", 
                transactions.getTotalElements(), accountId);
        
        return transactions.map(this::mapToResponse);
    }
    
    @Transactional
    @Auditable(action = AuditEntry.AuditAction.TRANSFER, entityType = "Transfer")
    @CacheEvict(value = {ACCOUNTS_CACHE, ACCOUNT_TRANSACTIONS_CACHE}, allEntries = true)
    public TransferResponse createTransfer(UUID userId, CreateTransferRequest request) {
        // Validate request
        if (request.getSourceAccountId().equals(request.getTargetAccountId())) {
            throw new IllegalArgumentException("Source and target accounts must be different");
        }
        
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }
        
        // Lock accounts in consistent order (by UUID comparison) to prevent deadlocks
        // Always lock the "smaller" UUID first
        Account sourceAccount;
        Account targetAccount;
        
        if (request.getSourceAccountId().compareTo(request.getTargetAccountId()) < 0) {
            // Source UUID is smaller, lock source first then target
            sourceAccount = accountRepository.findByIdWithLock(request.getSourceAccountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Source account not found"));
            targetAccount = accountRepository.findByIdWithLock(request.getTargetAccountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Target account not found"));
        } else {
            // Target UUID is smaller, lock target first then source
            targetAccount = accountRepository.findByIdWithLock(request.getTargetAccountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Target account not found"));
            sourceAccount = accountRepository.findByIdWithLock(request.getSourceAccountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Source account not found"));
        }
        
        // Validate user owns source account
        if (!sourceAccount.getUser().getId().equals(userId)) {
            throw new ForbiddenException("User is not authorized to transfer from this account");
        }
        
        // Check account status for source account (can it send money?)
        AccountStatusStrategy sourceStatusStrategy = statusStrategyFactory.getStrategy(sourceAccount);
        if (!sourceStatusStrategy.canTransfer(sourceAccount, request.getAmount())) {
            throw new IllegalStateException("Source account cannot transfer: " + sourceStatusStrategy.getRestrictionReason());
        }
        
        // Check account status for target account (can it receive money?)
        AccountStatusStrategy targetStatusStrategy = statusStrategyFactory.getStrategy(targetAccount);
        if (!targetStatusStrategy.canReceiveTransfer(targetAccount, request.getAmount())) {
            throw new IllegalStateException("Target account cannot receive transfer: " + targetStatusStrategy.getRestrictionReason());
        }
        
        // Validate source has sufficient funds
        TransactionStrategy transferStrategy = strategyFactory.getStrategy(TransactionType.TRANSFER, sourceAccount);
        transferStrategy.validateTransaction(sourceAccount, request.getAmount());
        
        // Generate shared transfer reference
        String transferReference = generateTransferReference();
        LocalDateTime timestamp = LocalDateTime.now();
        
        // Create withdrawal transaction from source account
        Transaction sourceTransaction = Transaction.builder()
                .id(UuidGenerator.generateUuidV7())
                .referenceNumber(transferReference + "-OUT")
                .type(TransactionType.TRANSFER)
                .amount(request.getAmount())
                .balanceAfter(sourceAccount.getBalance().subtract(request.getAmount()))
                .description(request.getDescription() != null ? 
                    "Transfer to " + targetAccount.getAccountNumber() + ": " + request.getDescription() : 
                    "Transfer to " + targetAccount.getAccountNumber())
                .status(TransactionStatus.COMPLETED)
                .account(sourceAccount)
                .transactionDate(timestamp)
                .build();
        
        // Create deposit transaction to target account
        Transaction targetTransaction = Transaction.builder()
                .id(UuidGenerator.generateUuidV7())
                .referenceNumber(transferReference + "-IN")
                .type(TransactionType.TRANSFER)
                .amount(request.getAmount())
                .balanceAfter(targetAccount.getBalance().add(request.getAmount()))
                .description(request.getDescription() != null ? 
                    "Transfer from " + sourceAccount.getAccountNumber() + ": " + request.getDescription() : 
                    "Transfer from " + sourceAccount.getAccountNumber())
                .status(TransactionStatus.COMPLETED)
                .account(targetAccount)
                .transactionDate(timestamp)
                .build();
        
        // Update account balances
        sourceAccount.setBalance(sourceTransaction.getBalanceAfter());
        targetAccount.setBalance(targetTransaction.getBalanceAfter());
        
        // Save everything (atomic transaction)
        Transaction savedSourceTransaction = transactionRepository.save(sourceTransaction);
        Transaction savedTargetTransaction = transactionRepository.save(targetTransaction);
        accountRepository.save(sourceAccount);
        accountRepository.save(targetAccount);
        
        // Publish events
        eventPublisher.publishEvent(new TransactionCompletedEvent(
                savedSourceTransaction.getId(),
                sourceAccount.getId(),
                sourceAccount.getUser().getId(),
                savedSourceTransaction.getReferenceNumber(),
                TransactionType.TRANSFER,
                savedSourceTransaction.getAmount(),
                savedSourceTransaction.getBalanceAfter()
        ));
        
        eventPublisher.publishEvent(new TransactionCompletedEvent(
                savedTargetTransaction.getId(),
                targetAccount.getId(),
                targetAccount.getUser().getId(),
                savedTargetTransaction.getReferenceNumber(),
                TransactionType.TRANSFER,
                savedTargetTransaction.getAmount(),
                savedTargetTransaction.getBalanceAfter()
        ));
        
        // Record metrics
        metricsCollector.recordTransaction(TransactionType.TRANSFER, request.getAmount(), 0L);
        
        log.info("Transfer {} completed from account {} to account {} for amount {}", 
                transferReference, sourceAccount.getAccountNumber(), 
                targetAccount.getAccountNumber(), request.getAmount());
        
        // Build response
        return TransferResponse.builder()
                .transferReference(transferReference)
                .sourceAccountId(sourceAccount.getId())
                .targetAccountId(targetAccount.getId())
                .amount(request.getAmount())
                .description(request.getDescription())
                .sourceTransaction(mapToResponse(savedSourceTransaction))
                .targetTransaction(mapToResponse(savedTargetTransaction))
                .timestamp(timestamp)
                .status("COMPLETED")
                .build();
    }
    
    private String generateTransferReference() {
        // Generate a unique transfer reference
        // Format: TRF + timestamp + random 4 digits
        String timestamp = LocalDateTime.now().format(REFERENCE_FORMATTER);
        int random = (int) (Math.random() * 10000);
        return String.format("TRF%s%04d", timestamp, random);
    }
}