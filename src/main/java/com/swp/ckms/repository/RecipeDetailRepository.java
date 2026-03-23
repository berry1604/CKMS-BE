package com.swp.ckms.repository;

import com.swp.ckms.entity.RecipeDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecipeDetailRepository extends JpaRepository<RecipeDetail, Long> {
    boolean existsByMaterial_IdAndRecipe_IsActiveTrue(Long materialId);
}
