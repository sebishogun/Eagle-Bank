package com.eaglebank.service;

import com.eaglebank.dto.request.CreateTransactionRequest;
import com.eaglebank.dto.response.TransactionResponse;
import com.eaglebank.entity.Account;
import com.eaglebank.entity.Transaction;
import com.eaglebank.entity.Transaction.TransactionStatus;
import com.eaglebank.entity.Transaction.TransactionType;
import com.eaglebank.exception.ForbiddenException;
import com.eaglebank.exception.InsufficientFundsException;
import com.eaglebank.exception.ResourceNotFoundException;
import com.eaglebank.repository.AccountRepository;
import com.eaglebank.repository.TransactionRepository;
import com.eaglebank.util.UuidGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private static final DateTimeFormatter REFERENCE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Transactional
    public TransactionResponse createTransaction(UUID userId, UUID accountId, CreateTransactionRequest request) {
        // Validate amount
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        // Find and validate account
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + accountId));
        
        // Check authorization
        validateAccountOwnership(account, userId);
        
        // Get current balance
        BigDecimal balanceAfter = getBigDecimal(request, account);

        // Create transaction
        Transaction transaction = Transaction.builder()
                .id(UuidGenerator.generateUuidV7())
                .referenceNumber(generateTransactionReference())
                .type(request.getTransactionType())
                .amount(request.getAmount())
                .balanceAfter(balanceAfter)
                .description(request.getDescription())
                .status(TransactionStatus.COMPLETED)
                .account(account)
                .transactionDate(LocalDateTime.now())
                .build();
        
        // Update account balance
        account.setBalance(balanceAfter);
        
        // Save transaction and account
        Transaction savedTransaction = transactionRepository.save(transaction);
        accountRepository.save(account);
        
        log.info("Created {} transaction {} for account {} with amount {}", 
                request.getTransactionType(), savedTransaction.getReferenceNumber(), 
                accountId, request.getAmount());
        
        return mapToResponse(savedTransaction);
    }

    private static BigDecimal getBigDecimal(CreateTransactionRequest request, Account account) {
        BigDecimal balanceBefore = account.getBalance();
        BigDecimal balanceAfter;

        // Calculate new balance based on transaction type
        if (request.getTransactionType() == TransactionType.WITHDRAWAL) {
            // Check sufficient funds
            if (balanceBefore.compareTo(request.getAmount()) < 0) {
                throw new InsufficientFundsException(
                    String.format("Insufficient funds. Available balance: %s, Requested amount: %s",
                        balanceBefore, request.getAmount())
                );
            }
            balanceAfter = balanceBefore.subtract(request.getAmount());
        } else {
            // DEPOSIT
            balanceAfter = balanceBefore.add(request.getAmount());
        }
        return balanceAfter;
    }

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