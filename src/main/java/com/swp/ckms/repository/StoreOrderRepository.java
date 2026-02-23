package com.swp.ckms.repository;

import com.swp.ckms.entity.OrderStatus;
import com.swp.ckms.entity.StoreOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StoreOrderRepository extends JpaRepository<StoreOrder, Long> {
    List<StoreOrder> findByStatus(OrderStatus status);
    List<StoreOrder> findByBatchId(Long batchId);
    List<StoreOrder> findByStore_StoreId(Long storeId);
}
