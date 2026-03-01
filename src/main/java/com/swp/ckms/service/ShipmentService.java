package com.swp.ckms.service;

import com.swp.ckms.dto.request.ConfirmDeliveryRequest;
import com.swp.ckms.dto.request.CreateShipmentRequest;
import com.swp.ckms.dto.response.ShipmentResponse;
import com.swp.ckms.enums.ShipmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ShipmentService {
    ShipmentResponse createShipment(CreateShipmentRequest request);
    ShipmentResponse prepareShipment(Long shipmentId);
    ShipmentResponse startTransit(Long shipmentId);
    ShipmentResponse confirmDelivery(Long shipmentId, ConfirmDeliveryRequest request);
    ShipmentResponse cancelShipment(Long shipmentId, String reason);
    ShipmentResponse getShipmentById(Long shipmentId);
    Page<ShipmentResponse> getShipments(ShipmentStatus status, Pageable pageable);
}