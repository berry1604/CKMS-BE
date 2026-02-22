package com.swp.ckms.repository;

import com.swp.ckms.entity.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, Long> {
    
    Optional<Recipe> findByProductIdAndIsActiveTrue(Long productId);
    
    List<Recipe> findByProductIdOrderByVersionDesc(Long productId);
    
    boolean existsByProductIdAndIsActiveTrue(Long productId);
}
