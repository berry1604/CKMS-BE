package com.swp.ckms.repository;

import com.swp.ckms.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    boolean existsByName(String name);
    
    // Find all active products with pagination
    Page<Product> findByIsActiveTrue(Pageable pageable);
    
    Optional<Product> findByIdAndIsActiveTrue(Long id);
    
    // Search by name containing (case insensitive) and active
    Page<Product> findByNameContainingIgnoreCaseAndIsActiveTrue(String name, Pageable pageable);
    
    // Filter by category and active
    Page<Product> findByCategoryIdAndIsActiveTrue(Long categoryId, Pageable pageable);
}
