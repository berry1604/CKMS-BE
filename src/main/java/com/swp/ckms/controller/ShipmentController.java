package com.swp.ckms.controller;

import com.swp.ckms.dto.request.ConfirmDeliveryRequest;
import com.swp.ckms.dto.request.CreateShipmentRequest;
import com.swp.ckms.dto.response.ApiResponse;
import com.swp.ckms.dto.response.ShipmentResponse;
import com.swp.ckms.enums.ShipmentStatus;
import com.swp.ckms.service.ShipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/shipments")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;

    // Coordinator tạo shipment
    @PostMapping
    @PreAuthorize("hasAuthority('CREATE_SHIPMENT')")
    public ResponseEntity<ApiResponse<ShipmentResponse>> createShipment(
            @Valid @RequestBody CreateShipmentRequest request) {

        ShipmentResponse response = shipmentService.createShipment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<ShipmentResponse>builder()
                .status(HttpStatus.CREATED.value())
                .message("Shipment created successfully")
                .data(response)
                .timestamp(LocalDateTime.now())
                .build());
    }

    // Kitchen staff chuẩn bị hàng xong
    @PatchMapping("/{id}/prepare")
    @PreAuthorize("hasAuthority('PREPARE_SHIPMENT')")
    public ResponseEntity<ApiResponse<ShipmentResponse>> prepareShipment(@PathVariable Long id) {

        ShipmentResponse response = shipmentService.prepareShipment(id);
        return ResponseEntity.ok(ApiResponse.<ShipmentResponse>builder()
                .status(HttpStatus.OK.value())
                .message("Shipment prepared successfully")
                .data(response)
                .timestamp(LocalDateTime.now())
                .build());
    }

    // Coordinator xác nhận xuất kho → đang giao
    @PatchMapping("/{id}/transit")
    @PreAuthorize("hasAuthority('START_SHIPMENT')")
    public ResponseEntity<ApiResponse<ShipmentResponse>> startTransit(@PathVariable Long id) {

        ShipmentResponse response = shipmentService.startTransit(id);
        return ResponseEntity.ok(ApiResponse.<ShipmentResponse>builder()
                .status(HttpStatus.OK.value())
                .message("Shipment is now in transit")
                .data(response)
                .timestamp(LocalDateTime.now())
                .build());
    }

    // Store staff xác nhận nhận hàng
        @PatchMapping("/{id}/stops/{stopId}/confirm")
    @PreAuthorize("hasAuthority('CONFIRM_SHIPMENT')")
    public ResponseEntity<ApiResponse<ShipmentResponse>> confirmDelivery(
            @PathVariable Long id,
                        @PathVariable Long stopId,
            @RequestBody(required = false) ConfirmDeliveryRequest request) {

                ShipmentResponse response = shipmentService.confirmDelivery(id, stopId, request);
        return ResponseEntity.ok(ApiResponse.<ShipmentResponse>builder()
                .status(HttpStatus.OK.value())
                .message("Delivery confirmed successfully")
                .data(response)
                .timestamp(LocalDateTime.now())
                .build());
    }

    // Coordinator hủy shipment
    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('CANCEL_SHIPMENT')")
    public ResponseEntity<ApiResponse<ShipmentResponse>> cancelShipment(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "No reason provided") String reason) {

        ShipmentResponse response = shipmentService.cancelShipment(id, reason);
        return ResponseEntity.ok(ApiResponse.<ShipmentResponse>builder()
                .status(HttpStatus.OK.value())
                .message("Shipment cancelled")
                .data(response)
                .timestamp(LocalDateTime.now())
                .build());
    }

    // Xem chi tiết shipment
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VIEW_SHIPMENT') or hasAnyRole('MANAGER', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<ApiResponse<ShipmentResponse>> getShipment(@PathVariable Long id) {

        ShipmentResponse response = shipmentService.getShipmentById(id);
        return ResponseEntity.ok(ApiResponse.<ShipmentResponse>builder()
                .status(HttpStatus.OK.value())
                .message("Shipment retrieved")
                .data(response)
                .timestamp(LocalDateTime.now())
                .build());
    }

    // Danh sách shipments (auto-filter by scope)
    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_SHIPMENT') or hasAnyRole('MANAGER', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<ApiResponse<Page<ShipmentResponse>>> getShipments(
            @RequestParam(required = false) ShipmentStatus status,
            Pageable pageable) {

        Page<ShipmentResponse> page = shipmentService.getShipments(status, pageable);
        return ResponseEntity.ok(ApiResponse.<Page<ShipmentResponse>>builder()
                .status(HttpStatus.OK.value())
                .message("Shipments retrieved")
                .data(page)
                .timestamp(LocalDateTime.now())
                .build());
    }
}