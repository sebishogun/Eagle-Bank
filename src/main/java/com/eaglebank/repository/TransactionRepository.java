package com.eaglebank.repository;

import com.eaglebank.entity.Transaction;
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
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    
    List<Transaction> findByAccountIdOrderByTransactionDateDesc(UUID accountId);
    
    Page<Transaction> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);
    
    Optional<Transaction> findByIdAndAccountId(UUID id, UUID accountId);
    
    @Query("SELECT t FROM Transaction t WHERE t.account.id = :accountId AND t.account.user.id = :userId")
    List<Transaction> findByAccountIdAndUserId(@Param("accountId") UUID accountId, @Param("userId") UUID userId);
    
    @Query("SELECT t FROM Transaction t WHERE t.id = :transactionId AND t.account.id = :accountId AND t.account.user.id = :userId")
    Optional<Transaction> findByIdAndAccountIdAndUserId(@Param("transactionId") UUID transactionId, 
                                                        @Param("accountId") UUID accountId, 
                                                        @Param("userId") UUID userId);
}