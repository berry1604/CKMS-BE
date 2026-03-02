package com.swp.ckms.repository;

import com.swp.ckms.entity.ShipmentSourcingRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShipmentSourcingRecordRepository extends JpaRepository<ShipmentSourcingRecord, Long> {
    List<ShipmentSourcingRecord> findByShipment_ShipmentId(Long shipmentId);
}
