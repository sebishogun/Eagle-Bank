package com.eaglebank.pattern.specification;

import com.eaglebank.entity.Account;
import com.eaglebank.entity.Account.AccountStatus;
import com.eaglebank.entity.Transaction;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class AccountSpecifications {
    
    public static Specification<Account> belongsToUser(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }
    
    public static Specification<Account> hasAccountType(String accountType) {
        return (root, query, cb) -> cb.equal(root.get("accountType"), accountType);
    }
    
    public static Specification<Account> hasStatus(AccountStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
    
    public static Specification<Account> balanceGreaterThan(BigDecimal amount) {
        return (root, query, cb) -> cb.greaterThan(root.get("balance"), amount);
    }
    
    public static Specification<Account> balanceLessThan(BigDecimal amount) {
        return (root, query, cb) -> cb.lessThan(root.get("balance"), amount);
    }
    
    public static Specification<Account> balanceBetween(BigDecimal min, BigDecimal max) {
        return (root, query, cb) -> cb.between(root.get("balance"), min, max);
    }
    
    public static Specification<Account> createdAfter(LocalDateTime date) {
        return (root, query, cb) -> cb.greaterThan(root.get("createdAt"), date);
    }
    
    public static Specification<Account> createdBefore(LocalDateTime date) {
        return (root, query, cb) -> cb.lessThan(root.get("createdAt"), date);
    }
    
    public static Specification<Account> accountNumberLike(String pattern) {
        return (root, query, cb) -> cb.like(root.get("accountNumber"), pattern);
    }
    
    public static Specification<Account> activeAccounts() {
        return hasStatus(AccountStatus.ACTIVE);
    }
    
    public static Specification<Account> inactiveAccounts() {
        return hasStatus(AccountStatus.INACTIVE);
    }
    
    public static Specification<Account> highValueAccounts(BigDecimal threshold) {
        return balanceGreaterThan(threshold).and(activeAccounts());
    }
    
    // Transaction-related specifications
    public static Specification<Account> hasTransactions() {
        return (root, query, cb) -> cb.isNotEmpty(root.get("transactions"));
    }
    
    public static Specification<Account> hasRecentTransactions(LocalDateTime since) {
        return (root, query, cb) -> {
            Subquery<Transaction> subquery = query.subquery(Transaction.class);
            Root<Transaction> transaction = subquery.from(Transaction.class);
            subquery.select(transaction)
                .where(
                    cb.equal(transaction.get("account"), root),
                    cb.greaterThan(transaction.get("transactionDate"), since)
                );
            return cb.exists(subquery);
        };
    }
    
    public static Specification<Account> transactionVolumeGreaterThan(BigDecimal volume, LocalDateTime since) {
        return (root, query, cb) -> {
            Subquery<BigDecimal> subquery = query.subquery(BigDecimal.class);
            Root<Transaction> transaction = subquery.from(Transaction.class);
            subquery.select(cb.sum(transaction.get("amount")))
                .where(
                    cb.equal(transaction.get("account"), root),
                    cb.greaterThan(transaction.get("transactionDate"), since),
                    cb.equal(transaction.get("status"), Transaction.TransactionStatus.COMPLETED)
                );
            return cb.greaterThan(subquery, volume);
        };
    }
    
    public static Specification<Account> hasTransactionOfType(Transaction.TransactionType type) {
        return (root, query, cb) -> {
            Join<Account, Transaction> transactionJoin = root.join("transactions", JoinType.LEFT);
            return cb.equal(transactionJoin.get("type"), type);
        };
    }
    
    public static Specification<Account> hasHighValueTransaction(BigDecimal threshold) {
        return (root, query, cb) -> {
            Subquery<Transaction> subquery = query.subquery(Transaction.class);
            Root<Transaction> transaction = subquery.from(Transaction.class);
            subquery.select(transaction)
                .where(
                    cb.equal(transaction.get("account"), root),
                    cb.greaterThanOrEqualTo(transaction.get("amount"), threshold),
                    cb.equal(transaction.get("status"), Transaction.TransactionStatus.COMPLETED)
                );
            return cb.exists(subquery);
        };
    }
    
    public static Specification<Account> transactionCountGreaterThan(Long count, LocalDateTime since) {
        return (root, query, cb) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<Transaction> transaction = subquery.from(Transaction.class);
            subquery.select(cb.count(transaction))
                .where(
                    cb.equal(transaction.get("account"), root),
                    cb.greaterThan(transaction.get("transactionDate"), since)
                );
            return cb.greaterThan(subquery, count);
        };
    }
}