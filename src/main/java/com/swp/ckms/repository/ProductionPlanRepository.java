package com.swp.ckms.repository;

import com.swp.ckms.entity.ProductionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ProductionPlanRepository extends JpaRepository<ProductionPlan, Long>, JpaSpecificationExecutor<ProductionPlan> {
    java.util.Optional<ProductionPlan> findByPlanIdAndKitchen_KitchenId(Long planId, Long kitchenId);

    @Query("SELECT p FROM ProductionPlan p LEFT JOIN FETCH p.materialRequirements WHERE p.planId = :planId")
    Optional<ProductionPlan> findByIdWithMaterials(@Param("planId") Long planId);
}
