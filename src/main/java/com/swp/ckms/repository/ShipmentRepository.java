package com.swp.ckms.repository;

import com.swp.ckms.entity.Shipment;
import com.swp.ckms.enums.ShipmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

import java.util.List;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    Page<Shipment> findByStatus(ShipmentStatus status, Pageable pageable);

    List<Shipment> findByProductionPlan_PlanId(Long planId);

    Optional<Shipment> findByAhamoveOrderId(String ahamoveOrderId);

    Page<Shipment> findDistinctByStops_Store_StoreId(Long storeId, Pageable pageable);

    Page<Shipment> findDistinctByStops_Store_StoreIdAndStatus(
            Long storeId, ShipmentStatus status, Pageable pageable);

    Page<Shipment> findDistinctByStops_Store_StoreIdAndStatusIn(
            Long storeId, List<ShipmentStatus> statuses, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT s FROM Shipment s JOIN s.stops stop WHERE stop.store.storeId = :storeId AND stop.status = :stopStatus")
    Page<Shipment> findByStoreIdAndStopStatus(
            @org.springframework.data.repository.query.Param("storeId") Long storeId, 
            @org.springframework.data.repository.query.Param("stopStatus") com.swp.ckms.enums.ShipmentStopStatus stopStatus, 
            Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT s FROM Shipment s JOIN s.stops stop WHERE stop.store.storeId = :storeId AND stop.status IN :stopStatuses")
    Page<Shipment> findByStoreIdAndStopStatusIn(
            @org.springframework.data.repository.query.Param("storeId") Long storeId, 
            @org.springframework.data.repository.query.Param("stopStatuses") List<com.swp.ckms.enums.ShipmentStopStatus> stopStatuses, 
            Pageable pageable);

    List<Shipment> findDistinctByStops_Store_StoreIdAndStatusAndDeliveredAtBetween(
            Long storeId, ShipmentStatus status, java.time.LocalDateTime start, java.time.LocalDateTime end);
}