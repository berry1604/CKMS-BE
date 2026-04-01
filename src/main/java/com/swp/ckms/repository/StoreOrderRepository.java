package com.swp.ckms.repository;

import com.swp.ckms.entity.StoreOrder;
import com.swp.ckms.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.swp.ckms.repository.projection.MaterialRequirementProjection;

import java.util.List;

@Repository
public interface StoreOrderRepository extends JpaRepository<StoreOrder, Long>, JpaSpecificationExecutor<StoreOrder> {
    List<StoreOrder> findByStatus(OrderStatus status);
    Page<StoreOrder> findByStatus(OrderStatus status, Pageable pageable);
    List<StoreOrder> findByBatchId(Long batchId);
    List<StoreOrder> findByStore_StoreId(Long storeId);
    Page<StoreOrder> findByStore_StoreId(Long storeId, Pageable pageable);
    Page<StoreOrder> findByStore_StoreIdAndStatus(Long storeId, OrderStatus status, Pageable pageable);
    
    List<StoreOrder> findByProductionPlan_PlanId(Long planId);

    @Modifying
    @Query("UPDATE StoreOrder o SET o.productionPlan = :plan, o.status = com.swp.ckms.enums.OrderStatus.SCHEDULED WHERE o.orderId IN :orderIds AND o.productionPlan IS NULL AND (o.status = com.swp.ckms.enums.OrderStatus.APPROVED OR o.status = com.swp.ckms.enums.OrderStatus.CONFIRMED)")
    int assignOrdersToPlan(@Param("plan") com.swp.ckms.entity.ProductionPlan plan, @Param("orderIds") List<Long> orderIds);

    @Query("""
        SELECT 
            rd.material AS material,
            SUM((CAST(od.quantity AS double) / r.yield) * rd.quantityNeeded) AS total
        FROM StoreOrder so
        JOIN so.orderDetails od
        JOIN od.product p
        JOIN Recipe r ON r.product.id = p.id AND r.isActive = true
        JOIN r.recipeDetails rd
        WHERE so.productionPlan.planId = :planId
        GROUP BY rd.material
    """)
    List<MaterialRequirementProjection> getMaterialRequirementsForPlan(@Param("planId") Long planId);

    @Modifying
    @Query("UPDATE StoreOrder o SET o.status = com.swp.ckms.enums.OrderStatus.ALLOCATED WHERE o.productionPlan.planId = :planId AND o.status = com.swp.ckms.enums.OrderStatus.SCHEDULED")
    int updateOrderStatusToReadyByPlanId(@Param("planId") Long planId);

    @Modifying
    @Query("UPDATE StoreOrder o SET o.productionPlan = NULL, o.status = com.swp.ckms.enums.OrderStatus.APPROVED WHERE o.productionPlan.planId = :planId AND o.status IN (com.swp.ckms.enums.OrderStatus.SCHEDULED, com.swp.ckms.enums.OrderStatus.LOCKED)")
    int releaseOrdersFromPlan(@Param("planId") Long planId);

    // List<StoreOrder> findByShipment_ShipmentId(Long shipmentId);
    List<StoreOrder> findByShipmentStop_StopId(Long stopId);
    List<StoreOrder> findByShipmentStop_Shipment_ShipmentId(Long shipmentId);

    @Query("SELECT SUM(od.quantity) FROM StoreOrder o JOIN o.orderDetails od WHERE o.orderId IN :orderIds")
    java.math.BigDecimal sumQuantityByOrderIds(@Param("orderIds") List<Long> orderIds);

    @Modifying
    @Query("UPDATE StoreOrder o SET o.status = :status WHERE o.productionPlan.planId = :planId AND o.status = com.swp.ckms.enums.OrderStatus.SCHEDULED")
    int updateStatusByPlanId(@Param("planId") Long planId, @Param("status") OrderStatus status);

    @Query("""
        SELECT SUM(od.quantity) 
        FROM StoreOrder o 
        JOIN o.orderDetails od 
        LEFT JOIN o.approvedByUser u
        LEFT JOIN o.productionPlan p
        WHERE (u.kitchen.kitchenId = :kitchenId OR p.kitchen.kitchenId = :kitchenId)
        AND o.deliveryDate = :deliveryDate 
        AND o.status IN (
            com.swp.ckms.enums.OrderStatus.APPROVED,
            com.swp.ckms.enums.OrderStatus.SCHEDULED,
            com.swp.ckms.enums.OrderStatus.LOCKED,
            com.swp.ckms.enums.OrderStatus.ALLOCATED,
            com.swp.ckms.enums.OrderStatus.IN_TRANSIT,
            com.swp.ckms.enums.OrderStatus.DELIVERED,
            com.swp.ckms.enums.OrderStatus.CONFIRMED
        )
    """)
    java.math.BigDecimal sumQuantityByKitchenAndDeliveryDate(
            @Param("kitchenId") Long kitchenId, 
            @Param("deliveryDate") java.time.LocalDate deliveryDate);

    @Query("""
        SELECT o FROM StoreOrder o 
        LEFT JOIN o.approvedByUser u
        WHERE (u.kitchen.kitchenId = :kitchenId)
        AND o.deliveryDate = :deliveryDate
        AND o.status = com.swp.ckms.enums.OrderStatus.APPROVED
        AND o.productionPlan IS NULL
        ORDER BY o.deliveryDate ASC, o.totalAmount DESC, o.orderDate ASC
    """)
    List<StoreOrder> findUnassignedApprovedOrders(
            @Param("kitchenId") Long kitchenId, 
            @Param("deliveryDate") java.time.LocalDate deliveryDate);

    @Query("""
        SELECT SUM(od.quantity) 
        FROM StoreOrder o 
        JOIN o.orderDetails od 
        WHERE o.deliveryDate = :deliveryDate 
        AND o.status IN (
            com.swp.ckms.enums.OrderStatus.APPROVED,
            com.swp.ckms.enums.OrderStatus.SCHEDULED,
            com.swp.ckms.enums.OrderStatus.LOCKED,
            com.swp.ckms.enums.OrderStatus.ALLOCATED,
            com.swp.ckms.enums.OrderStatus.IN_TRANSIT,
            com.swp.ckms.enums.OrderStatus.DELIVERED,
            com.swp.ckms.enums.OrderStatus.CONFIRMED
        )
    """)
    java.math.BigDecimal sumTotalQuantityByDeliveryDate(
            @Param("deliveryDate") java.time.LocalDate deliveryDate);

    @Query("""
        SELECT o FROM StoreOrder o 
        WHERE o.deliveryDate = :deliveryDate
        AND o.status = com.swp.ckms.enums.OrderStatus.APPROVED
        AND o.productionPlan IS NULL
        ORDER BY o.deliveryDate ASC, o.totalAmount DESC, o.orderDate ASC
    """)
    List<StoreOrder> findAllUnassignedApprovedOrders(
            @Param("deliveryDate") java.time.LocalDate deliveryDate);
}
