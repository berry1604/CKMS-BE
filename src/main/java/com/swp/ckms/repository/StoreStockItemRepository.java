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

    @Override
    @EntityGraph(attributePaths = {"product", "warehouse", "warehouse.store"})
    Page<StoreStockItem> findAll(Specification<StoreStockItem> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"product", "warehouse", "warehouse.store"})
    Optional<StoreStockItem> findById(Long id);
}
