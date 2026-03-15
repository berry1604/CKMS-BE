package com.swp.ckms.repository;

import com.swp.ckms.entity.ShipmentStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShipmentStopRepository extends JpaRepository<ShipmentStop, Long> {
    // List<StoreOrder> findByShipmentStop_Shipment_ShipmentId(Long shipmentId);
}