package com.eaglebank.pattern.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

public interface Specification<T> {
    
    Predicate toPredicate(Root<T> root, CriteriaQuery<?> query, CriteriaBuilder cb);
    
    default Specification<T> and(Specification<T> other) {
        return (root, query, cb) -> {
            Predicate thisPredicate = this.toPredicate(root, query, cb);
            Predicate otherPredicate = other.toPredicate(root, query, cb);
            return cb.and(thisPredicate, otherPredicate);
        };
    }
    
    default Specification<T> or(Specification<T> other) {
        return (root, query, cb) -> {
            Predicate thisPredicate = this.toPredicate(root, query, cb);
            Predicate otherPredicate = other.toPredicate(root, query, cb);
            return cb.or(thisPredicate, otherPredicate);
        };
    }
    
    default Specification<T> not() {
        return (root, query, cb) -> cb.not(this.toPredicate(root, query, cb));
    }
}