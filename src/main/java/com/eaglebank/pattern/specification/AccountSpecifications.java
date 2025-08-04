package com.eaglebank.pattern.specification;

import com.eaglebank.entity.Account;
import com.eaglebank.entity.Account.AccountStatus;

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
}