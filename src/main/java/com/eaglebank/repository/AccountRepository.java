package com.eaglebank.repository;

import com.eaglebank.dto.response.AccountTransactionSummary;
import com.eaglebank.entity.Account;
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
public interface AccountRepository extends JpaRepository<Account, UUID>, JpaSpecificationExecutor<Account> {
    
    // Basic queries
    Optional<Account> findByIdAndUserId(UUID id, UUID userId);
    
    List<Account> findByUserId(UUID userId);
    
    Page<Account> findByUserId(UUID userId, Pageable pageable);
    
    Optional<Account> findByAccountNumber(String accountNumber);
    
    Optional<Account> findByAccountNumberAndUserId(String accountNumber, UUID userId);
    
    boolean existsByAccountNumber(String accountNumber);
    
    boolean existsByIdAndUserId(UUID id, UUID userId);
    
    // Count queries
    long countByUserId(UUID userId);
    
    long countByUserIdAndAccountType(UUID userId, String accountType);
    
    long countByAccountType(Account.AccountType accountType);
    
    long countByAccountTypeAndStatus(Account.AccountType accountType, Account.AccountStatus status);
    
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.account.id = :accountId")
    long countTransactionsByAccountId(@Param("accountId") UUID accountId);
    
    // Balance queries
    @Query("SELECT SUM(a.balance) FROM Account a WHERE a.user.id = :userId")
    BigDecimal getTotalBalanceByUserId(@Param("userId") UUID userId);
    
    @Query("SELECT SUM(a.balance) FROM Account a WHERE a.user.id = :userId AND a.accountType = :accountType")
    BigDecimal getTotalBalanceByUserIdAndType(@Param("userId") UUID userId, @Param("accountType") String accountType);
    
    @Query("SELECT COALESCE(SUM(a.balance), 0) FROM Account a WHERE a.accountType = :accountType AND a.status = :status")
    BigDecimal sumBalanceByAccountTypeAndStatus(@Param("accountType") Account.AccountType accountType, @Param("status") Account.AccountStatus status);
    
    // Advanced queries
    List<Account> findByUserIdAndAccountType(UUID userId, String accountType);
    
    List<Account> findByUserIdAndStatus(UUID userId, Account.AccountStatus status);
    
    Page<Account> findByUserIdAndAccountType(UUID userId, String accountType, Pageable pageable);
    
    @Query("SELECT a FROM Account a WHERE a.user.id = :userId AND a.balance > :minBalance")
    List<Account> findAccountsWithMinimumBalance(@Param("userId") UUID userId, @Param("minBalance") BigDecimal minBalance);
    
    @Query("SELECT a FROM Account a WHERE a.user.id = :userId AND a.balance BETWEEN :minBalance AND :maxBalance")
    List<Account> findAccountsInBalanceRange(@Param("userId") UUID userId, 
                                            @Param("minBalance") BigDecimal minBalance, 
                                            @Param("maxBalance") BigDecimal maxBalance);
    
    // Status queries
    Page<Account> findByStatus(Account.AccountStatus status, Pageable pageable);
    
    long countByStatus(Account.AccountStatus status);
    
    // Transaction-based account searches
    @Query("SELECT DISTINCT a FROM Account a JOIN a.transactions t WHERE t.amount >= :minAmount AND t.status = 'COMPLETED'")
    List<Account> findAccountsByMinimumTransactionAmount(@Param("minAmount") BigDecimal minAmount);
    
    @Query("SELECT DISTINCT a FROM Account a JOIN a.transactions t WHERE a.user.id = :userId AND t.transactionDate BETWEEN :startDate AND :endDate")
    List<Account> findAccountsByUserAndTransactionDateRange(
        @Param("userId") UUID userId, 
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate
    );
    
    @Query("SELECT DISTINCT a FROM Account a JOIN a.transactions t WHERE t.amount >= :threshold AND t.type = :type AND a.status = 'ACTIVE'")
    Page<Account> findActiveAccountsWithHighValueTransactions(
        @Param("threshold") BigDecimal threshold,
        @Param("type") Transaction.TransactionType type,
        Pageable pageable
    );
    
    @Query("""
        SELECT a FROM Account a 
        WHERE a.id IN (
            SELECT DISTINCT t.account.id FROM Transaction t 
            WHERE t.transactionDate >= :since 
            GROUP BY t.account.id 
            HAVING COUNT(t) >= :minTransactions
        )
        """)
    List<Account> findAccountsWithMinimumTransactionCount(
        @Param("since") LocalDateTime since,
        @Param("minTransactions") Long minTransactions
    );
    
    @Query("""
        SELECT new com.eaglebank.dto.response.AccountTransactionSummary(
            a.id, a.accountNumber, a.user.id,
            COUNT(t), SUM(t.amount), AVG(t.amount)
        )
        FROM Account a LEFT JOIN a.transactions t
        WHERE a.user.id = :userId
        GROUP BY a.id, a.accountNumber, a.user.id
        """)
    List<AccountTransactionSummary> getAccountTransactionSummariesForUser(@Param("userId") UUID userId);
}