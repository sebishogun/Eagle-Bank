package com.eaglebank.repository.impl;

import com.eaglebank.entity.Account;
import com.eaglebank.pattern.specification.Specification;
import com.eaglebank.repository.SpecificationExecutor;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepositoryImpl extends SpecificationExecutorImpl<Account> implements SpecificationExecutor<Account> {
    
    public AccountRepositoryImpl(EntityManager entityManager) {
        super(entityManager, Account.class);
    }
}