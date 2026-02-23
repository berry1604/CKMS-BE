package com.swp.ckms.repository;

import com.swp.ckms.entity.OrderStatus;
import com.swp.ckms.entity.StoreOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Repository
public interface StoreOrderRepository extends JpaRepository<StoreOrder, Long> {
    List<StoreOrder> findByStatus(OrderStatus status);
    Page<StoreOrder> findByStatus(OrderStatus status, Pageable pageable);
    List<StoreOrder> findByBatchId(Long batchId);
    List<StoreOrder> findByStore_StoreId(Long storeId);
    Page<StoreOrder> findByStore_StoreId(Long storeId, Pageable pageable);
    Page<StoreOrder> findByStore_StoreIdAndStatus(Long storeId, OrderStatus status, Pageable pageable);
}
