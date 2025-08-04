package com.eaglebank.repository.impl;

import com.eaglebank.entity.Transaction;
import com.eaglebank.pattern.specification.Specification;
import com.eaglebank.repository.SpecificationExecutor;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class TransactionRepositoryImpl extends SpecificationExecutorImpl<Transaction> implements SpecificationExecutor<Transaction> {
    
    public TransactionRepositoryImpl(EntityManager entityManager) {
        super(entityManager, Transaction.class);
    }
}