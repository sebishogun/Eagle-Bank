package com.eaglebank.repository;

import com.eaglebank.pattern.specification.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface SpecificationExecutor<T> {
    
    Optional<T> findOne(Specification<T> spec);
    
    List<T> findAll(Specification<T> spec);
    
    Page<T> findAll(Specification<T> spec, Pageable pageable);
    
    long count(Specification<T> spec);
    
    boolean exists(Specification<T> spec);
}