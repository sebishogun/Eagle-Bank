package com.eaglebank.repository.impl;

import com.eaglebank.pattern.specification.Specification;
import com.eaglebank.repository.SpecificationExecutor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public abstract class SpecificationExecutorImpl<T> implements SpecificationExecutor<T> {
    
    protected final EntityManager entityManager;
    protected final Class<T> entityClass;
    
    @Override
    public Optional<T> findOne(Specification<T> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(entityClass);
        Root<T> root = query.from(entityClass);
        
        Predicate predicate = spec.toPredicate(root, query, cb);
        query.where(predicate);
        
        TypedQuery<T> typedQuery = entityManager.createQuery(query);
        typedQuery.setMaxResults(1);
        
        List<T> results = typedQuery.getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
    
    @Override
    public List<T> findAll(Specification<T> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(entityClass);
        Root<T> root = query.from(entityClass);
        
        Predicate predicate = spec.toPredicate(root, query, cb);
        query.where(predicate);
        
        return entityManager.createQuery(query).getResultList();
    }
    
    @Override
    public Page<T> findAll(Specification<T> spec, Pageable pageable) {
        // Get total count
        long total = count(spec);
        
        // Get paginated results
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(entityClass);
        Root<T> root = query.from(entityClass);
        
        Predicate predicate = spec.toPredicate(root, query, cb);
        query.where(predicate);
        
        // Apply sorting if specified
        if (pageable.getSort().isSorted()) {
            // Implementation would handle sorting
        }
        
        TypedQuery<T> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        
        List<T> results = typedQuery.getResultList();
        return new PageImpl<>(results, pageable, total);
    }
    
    @Override
    public long count(Specification<T> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<T> root = query.from(entityClass);
        
        Predicate predicate = spec.toPredicate(root, query, cb);
        query.where(predicate);
        query.select(cb.count(root));
        
        return entityManager.createQuery(query).getSingleResult();
    }
    
    @Override
    public boolean exists(Specification<T> spec) {
        return count(spec) > 0;
    }
}