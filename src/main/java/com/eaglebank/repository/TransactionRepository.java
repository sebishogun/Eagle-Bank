package com.eaglebank.repository;

import com.eaglebank.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {
    
    // Basic queries
    Optional<Transaction> findByIdAndAccountId(UUID id, UUID accountId);
    
    Optional<Transaction> findByReferenceNumber(String referenceNumber);
    
    List<Transaction> findByAccountId(UUID accountId);
    
    Page<Transaction> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);
    
    Page<Transaction> findByAccountIdOrderByTransactionDateDesc(UUID accountId, Pageable pageable);
    
    // Type-based queries
    List<Transaction> findByAccountIdAndType(UUID accountId, Transaction.TransactionType type);
    
    Page<Transaction> findByAccountIdAndType(UUID accountId, Transaction.TransactionType type, Pageable pageable);
    
    // Date range queries
    List<Transaction> findByAccountIdAndTransactionDateBetween(UUID accountId, LocalDateTime start, LocalDateTime end);
    
    Page<Transaction> findByAccountIdAndTransactionDateBetween(UUID accountId, LocalDateTime start, LocalDateTime end, Pageable pageable);
    
    // Amount queries
    @Query("SELECT t FROM Transaction t WHERE t.account.id = :accountId AND t.amount > :amount")
    List<Transaction> findLargeTransactions(@Param("accountId") UUID accountId, @Param("amount") BigDecimal amount);
    
    @Query("SELECT t FROM Transaction t WHERE t.account.id = :accountId AND t.amount BETWEEN :minAmount AND :maxAmount")
    List<Transaction> findTransactionsInAmountRange(@Param("accountId") UUID accountId,
                                                   @Param("minAmount") BigDecimal minAmount,
                                                   @Param("maxAmount") BigDecimal maxAmount);
    
    // Aggregate queries
    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.account.id = :accountId AND t.type = :type")
    BigDecimal getTotalAmountByAccountIdAndType(@Param("accountId") UUID accountId, 
                                               @Param("type") Transaction.TransactionType type);
    
    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.account.id = :accountId AND t.type = :type AND t.transactionDate BETWEEN :start AND :end")
    BigDecimal getTotalAmountByAccountIdAndTypeAndDateRange(@Param("accountId") UUID accountId,
                                                           @Param("type") Transaction.TransactionType type,
                                                           @Param("start") LocalDateTime start,
                                                           @Param("end") LocalDateTime end);
    
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.account.id = :accountId AND t.type = :type")
    long countByAccountIdAndType(@Param("accountId") UUID accountId, @Param("type") Transaction.TransactionType type);
    
    // User-level queries
    @Query("SELECT t FROM Transaction t WHERE t.account.user.id = :userId ORDER BY t.transactionDate DESC")
    Page<Transaction> findByUserId(@Param("userId") UUID userId, Pageable pageable);
    
    @Query("SELECT t FROM Transaction t WHERE t.account.user.id = :userId AND t.type = :type ORDER BY t.transactionDate DESC")
    Page<Transaction> findByUserIdAndType(@Param("userId") UUID userId, 
                                         @Param("type") Transaction.TransactionType type, 
                                         Pageable pageable);
    
    // Status queries
    List<Transaction> findByAccountIdAndStatus(UUID accountId, Transaction.TransactionStatus status);
    
    Page<Transaction> findByStatus(Transaction.TransactionStatus status, Pageable pageable);
    
    // Last transaction query
    Optional<Transaction> findTopByAccountIdOrderByTransactionDateDesc(UUID accountId);
}