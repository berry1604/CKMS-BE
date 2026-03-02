package com.swp.ckms.repository;

import com.swp.ckms.entity.StoreStockItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StoreStockItemRepository extends JpaRepository<StoreStockItem, Long>, JpaSpecificationExecutor<StoreStockItem> {

    @EntityGraph(attributePaths = {"product", "warehouse", "warehouse.store"})
    Optional<StoreStockItem> findById(Long id);

    @org.springframework.data.jpa.repository.Query("SELECT new com.swp.ckms.dto.response.StoreStockBatchResponse(" +
           "s.id, p.id, p.name, s.quantity, s.expiryDate, pp.planId, pp.batchCode) " +
           "FROM StoreStockItem s " +
           "JOIN s.product p " +
           "JOIN s.warehouse w " +
           "JOIN w.store st " +
           "LEFT JOIN s.productionPlan pp " +
           "WHERE st.id = :storeId AND p.id = :productId AND s.quantity > 0 " +
           "ORDER BY s.expiryDate ASC NULLS LAST, s.id ASC")
    java.util.List<com.swp.ckms.dto.response.StoreStockBatchResponse> getProductBatches(
            @org.springframework.data.repository.query.Param("storeId") Long storeId, 
            @org.springframework.data.repository.query.Param("productId") Long productId);

    @org.springframework.data.jpa.repository.Query("SELECT SUM(s.quantity) FROM StoreStockItem s WHERE s.warehouse.warehouseId = :warehouseId")
    java.math.BigDecimal getTotalQuantityByWarehouseId(@org.springframework.data.repository.query.Param("warehouseId") Long warehouseId);
}
