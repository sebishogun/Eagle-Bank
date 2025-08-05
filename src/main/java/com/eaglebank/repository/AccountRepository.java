package com.eaglebank.repository;

import com.eaglebank.entity.Account;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID>, SpecificationExecutor<Account> {
    
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
    
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.account.id = :accountId")
    long countTransactionsByAccountId(@Param("accountId") UUID accountId);
    
    // Balance queries
    @Query("SELECT SUM(a.balance) FROM Account a WHERE a.user.id = :userId")
    BigDecimal getTotalBalanceByUserId(@Param("userId") UUID userId);
    
    @Query("SELECT SUM(a.balance) FROM Account a WHERE a.user.id = :userId AND a.accountType = :accountType")
    BigDecimal getTotalBalanceByUserIdAndType(@Param("userId") UUID userId, @Param("accountType") String accountType);
    
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
}