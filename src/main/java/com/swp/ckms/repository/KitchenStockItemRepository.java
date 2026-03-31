package com.swp.ckms.repository;

import com.swp.ckms.entity.KitchenStockItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface KitchenStockItemRepository extends JpaRepository<KitchenStockItem, Long> {
    
    List<KitchenStockItem> findByWarehouse_WarehouseId(Long warehouseId);

    List<KitchenStockItem> findByWarehouse_WarehouseIdAndMaterial_Id(Long warehouseId, Long materialId);

    List<KitchenStockItem> findByWarehouse_WarehouseIdAndProduct_Id(Long warehouseId, Long productId);

    @Query("SELECT SUM(k.quantity) FROM KitchenStockItem k WHERE k.warehouse.warehouseId = :warehouseId AND k.material.id = :materialId")
    BigDecimal sumMaterialQuantityByWarehouseId(@Param("warehouseId") Long warehouseId, @Param("materialId") Long materialId);

    @Query("SELECT SUM(k.quantity) FROM KitchenStockItem k WHERE k.warehouse.warehouseId = :warehouseId AND k.product.id = :productId")
    BigDecimal sumProductQuantityByWarehouseId(@Param("warehouseId") Long warehouseId, @Param("productId") Long productId);

    @Query("""
        SELECT k.material.id AS materialId,
               SUM(k.quantity) AS totalQuantity
        FROM KitchenStockItem k
        WHERE k.warehouse.warehouseId = :warehouseId
        AND k.material.id IN :materialIds
        GROUP BY k.material.id
    """)
    List<com.swp.ckms.repository.projection.MaterialStockProjection> getAvailableStockForMaterials(
            @Param("warehouseId") Long warehouseId, 
            @Param("materialIds") List<Long> materialIds);

        @Query("""
                SELECT k.material.id AS materialId,
                           SUM(k.quantity - COALESCE(k.reservedQuantity, 0)) AS totalQuantity
                FROM KitchenStockItem k
                WHERE k.warehouse.warehouseId = :warehouseId
                AND k.material.id IN :materialIds
                GROUP BY k.material.id
        """)
        List<com.swp.ckms.repository.projection.MaterialStockProjection> getNetAvailableStockForMaterials(
                        @Param("warehouseId") Long warehouseId,
                        @Param("materialIds") List<Long> materialIds);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT k FROM KitchenStockItem k
        WHERE k.warehouse.warehouseId = :warehouseId
        AND k.material.id IN :materialIds
        ORDER BY k.material.id ASC,
                 k.expiryDate ASC NULLS LAST,
                 k.id ASC
    """)
    List<KitchenStockItem> lockMaterialsForDeduction(
            @Param("warehouseId") Long warehouseId,
            @Param("materialIds") List<Long> materialIds);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT k FROM KitchenStockItem k
        WHERE k.warehouse.warehouseId = :warehouseId
        AND k.product.id IN :productIds
        ORDER BY k.product.id ASC,
                 k.expiryDate ASC NULLS LAST,
                 k.id ASC
    """)
    List<KitchenStockItem> lockProductsForDeduction(
            @Param("warehouseId") Long warehouseId,
            @Param("productIds") List<Long> productIds);

    @Query("SELECT k FROM KitchenStockItem k WHERE k.warehouse.warehouseId = :warehouseId AND k.product.id = :productId AND k.productionPlan.planId = :planId")
    Optional<KitchenStockItem> findByWarehouseAndProductAndPlan(
            @Param("warehouseId") Long warehouseId, 
            @Param("productId") Long productId, 
            @Param("planId") Long planId);

}
