package com.eaglebank.pattern.specification;

import com.eaglebank.entity.Transaction;
import com.eaglebank.entity.Transaction.TransactionType;
import com.eaglebank.entity.Transaction.TransactionStatus;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class TransactionSpecifications {
    
    public static Specification<Transaction> forAccount(UUID accountId) {
        return (root, query, cb) -> cb.equal(root.get("account").get("id"), accountId);
    }
    
    public static Specification<Transaction> forUser(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("account").get("user").get("id"), userId);
    }
    
    public static Specification<Transaction> ofType(TransactionType type) {
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }
    
    public static Specification<Transaction> withStatus(TransactionStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
    
    public static Specification<Transaction> amountGreaterThan(BigDecimal amount) {
        return (root, query, cb) -> cb.greaterThan(root.get("amount"), amount);
    }
    
    public static Specification<Transaction> amountLessThan(BigDecimal amount) {
        return (root, query, cb) -> cb.lessThan(root.get("amount"), amount);
    }
    
    public static Specification<Transaction> amountBetween(BigDecimal min, BigDecimal max) {
        return (root, query, cb) -> cb.between(root.get("amount"), min, max);
    }
    
    public static Specification<Transaction> transactedAfter(LocalDateTime date) {
        return (root, query, cb) -> cb.greaterThan(root.get("transactionDate"), date);
    }
    
    public static Specification<Transaction> transactedBefore(LocalDateTime date) {
        return (root, query, cb) -> cb.lessThan(root.get("transactionDate"), date);
    }
    
    public static Specification<Transaction> transactedBetween(LocalDateTime start, LocalDateTime end) {
        return (root, query, cb) -> cb.between(root.get("transactionDate"), start, end);
    }
    
    public static Specification<Transaction> descriptionContains(String keyword) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("description")), "%" + keyword.toLowerCase() + "%");
    }
    
    public static Specification<Transaction> referenceNumberEquals(String referenceNumber) {
        return (root, query, cb) -> cb.equal(root.get("referenceNumber"), referenceNumber);
    }
    
    public static Specification<Transaction> completedTransactions() {
        return withStatus(TransactionStatus.COMPLETED);
    }
    
    public static Specification<Transaction> pendingTransactions() {
        return withStatus(TransactionStatus.PENDING);
    }
    
    public static Specification<Transaction> largeTransactions(BigDecimal threshold) {
        return amountGreaterThan(threshold).and(completedTransactions());
    }
    
    public static Specification<Transaction> deposits() {
        return ofType(TransactionType.DEPOSIT);
    }
    
    public static Specification<Transaction> withdrawals() {
        return ofType(TransactionType.WITHDRAWAL);
    }
}