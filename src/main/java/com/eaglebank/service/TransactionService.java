package com.eaglebank.service;

import com.eaglebank.audit.Auditable;
import com.eaglebank.audit.AuditEntry;
import com.eaglebank.dto.request.CreateTransactionRequest;
import com.eaglebank.dto.response.TransactionResponse;
import com.eaglebank.entity.Account;
import com.eaglebank.entity.Transaction;
import com.eaglebank.entity.Transaction.TransactionStatus;
import com.eaglebank.entity.Transaction.TransactionType;
import com.eaglebank.pattern.observer.EventPublisher;
import com.eaglebank.event.TransactionCompletedEvent;
import com.eaglebank.exception.ForbiddenException;
import com.eaglebank.exception.InsufficientFundsException;
import com.eaglebank.exception.ResourceNotFoundException;
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
        
        // Find and validate account
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + accountId));
        
        // Check authorization
        validateAccountOwnership(account, userId);
        
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
    @Cacheable(value = TRANSACTIONS_CACHE, key = "#transactionId")
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
}