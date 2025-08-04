package com.eaglebank.repository;

import com.eaglebank.entity.Account;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    
    Page<Account> findByUserId(UUID userId, Pageable pageable);
    
    Optional<Account> findByAccountNumber(String accountNumber);
    
    boolean existsByAccountNumber(String accountNumber);
    
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.account.id = :accountId")
    long countTransactionsByAccountId(@Param("accountId") UUID accountId);
    
    long countByUserId(UUID userId);
}