package com.swp.ckms.repository;

import com.swp.ckms.entity.AllocationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AllocationItemRepository extends JpaRepository<AllocationItem, Long> {
    List<AllocationItem> findByProductionPlan_PlanId(Long planId);
    List<AllocationItem> findByOrder_OrderId(Long orderId);
}
