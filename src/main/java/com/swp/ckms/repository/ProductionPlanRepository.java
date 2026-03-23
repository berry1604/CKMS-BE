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

    @Query("""
        SELECT SUM(od.quantity) 
        FROM ProductionPlan p 
        JOIN StoreOrder o ON o.productionPlan.planId = p.planId
        JOIN o.orderDetails od
        WHERE p.kitchen.kitchenId = :kitchenId 
        AND p.plannedDate = :plannedDate 
        AND p.status <> com.swp.ckms.enums.ProductionPlanStatus.CANCELLED
    """)
    java.math.BigDecimal sumPlannedQuantityByKitchenAndDate(
            @Param("kitchenId") Long kitchenId, 
            @Param("plannedDate") java.time.LocalDate plannedDate);

    long countByPlannedDateAndKitchen_KitchenId(java.time.LocalDate plannedDate, Long kitchenId);

    boolean existsByKitchen_KitchenIdAndStatus(Long kitchenId, com.swp.ckms.enums.ProductionPlanStatus status);

    long countByKitchen_KitchenIdAndStatusIn(
            Long kitchenId,
            java.util.List<com.swp.ckms.enums.ProductionPlanStatus> statuses);

    @Query("""
        SELECT COALESCE(SUM(od.quantity), 0) 
        FROM ProductionPlan p 
        JOIN StoreOrder o ON o.productionPlan.planId = p.planId
        JOIN o.orderDetails od
        WHERE p.kitchen.kitchenId = :kitchenId 
        AND p.status IN :statuses
    """)
    java.math.BigDecimal sumPlannedQuantityByKitchenAndStatuses(
            @Param("kitchenId") Long kitchenId,
            @Param("statuses") java.util.List<com.swp.ckms.enums.ProductionPlanStatus> statuses);
}
