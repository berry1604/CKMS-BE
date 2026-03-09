package com.swp.ckms.repository;

import com.swp.ckms.entity.Shipment;
import com.swp.ckms.enums.ShipmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    Page<Shipment> findByStore_StoreId(Long storeId, Pageable pageable);

    Page<Shipment> findByStatus(ShipmentStatus status, Pageable pageable);

    Page<Shipment> findByStore_StoreIdAndStatus(Long storeId, ShipmentStatus status, Pageable pageable);

    List<Shipment> findByProductionPlan_PlanId(Long planId);

    List<Shipment> findAllByStore_StoreIdAndStatusAndDeliveredAtBetween(
            Long storeId, ShipmentStatus status, java.time.LocalDateTime start, java.time.LocalDateTime end);

    Optional<Shipment> findByAhamoveOrderId(String ahamoveOrderId);
}