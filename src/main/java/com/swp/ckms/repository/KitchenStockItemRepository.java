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
}
