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

    @org.springframework.data.jpa.repository.Query("SELECT r FROM Recipe r JOIN r.recipeDetails d WHERE d.material.id = :materialId AND r.isActive = true")
    List<Recipe> findActiveRecipesByMaterialId(@org.springframework.data.repository.query.Param("materialId") Long materialId);
}
