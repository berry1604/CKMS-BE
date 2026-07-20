package com.swp.ckms.controller;

import com.swp.ckms.dto.request.ApprovalMaterialPreviewRequest;
import com.swp.ckms.dto.request.StoreOrderRequest;
import com.swp.ckms.dto.response.ApprovalMaterialPreviewResponse;
import com.swp.ckms.dto.response.StoreOrderResponse;
import com.swp.ckms.service.StoreOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.swp.ckms.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class StoreOrderController {

    private final StoreOrderService storeOrderService;

    @PostMapping
    @PreAuthorize("hasAuthority('CREATE_STORE_ORDER')")
    public ResponseEntity<StoreOrderResponse> createOrder(@Valid @RequestBody StoreOrderRequest request, Authentication authentication) {
        StoreOrderResponse response = storeOrderService.createOrder(request, authentication.getName());
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/my")
    @PreAuthorize("hasAuthority('VIEW_STORE_ORDER') or hasAuthority('VIEW_MY_ORDERS')")
    public ResponseEntity<Page<StoreOrderResponse>> getMyOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "orderId") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            Authentication authentication) {
        
        org.springframework.data.domain.Sort sort = sortDir.equalsIgnoreCase("desc") 
                ? org.springframework.data.domain.Sort.by(sortBy).descending() 
                : org.springframework.data.domain.Sort.by(sortBy).ascending();
        Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, sort);
                
        Page<StoreOrderResponse> response = storeOrderService.getMyOrders(authentication.getName(), status, pageable);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_STORE_ORDER') or hasAnyRole('MANAGER', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<Page<StoreOrderResponse>> getAllOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "orderDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        
        // Anti-exploit: Cap page size
        int pageSize = Math.min(size, 100);
        
        org.springframework.data.domain.Sort sort = sortDir.equalsIgnoreCase("desc") 
                ? org.springframework.data.domain.Sort.by(sortBy).descending() 
                : org.springframework.data.domain.Sort.by(sortBy).ascending();
        Pageable pageable = org.springframework.data.domain.PageRequest.of(page, pageSize, sort);
        
        Page<StoreOrderResponse> response = storeOrderService.getAllOrders(status, pageable);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VIEW_STORE_ORDER') or hasAuthority('VIEW_MY_ORDERS') or hasAnyRole('MANAGER', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<StoreOrderResponse> getOrderById(@PathVariable Long id) {
        StoreOrderResponse response = storeOrderService.getOrderById(id);
        return ResponseEntity.ok(response);
    }
    
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('APPROVE_STORE_ORDER')")
    public ResponseEntity<StoreOrderResponse> updateOrderStatus(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body) {
        String statusStr = body.get("status");
        if (statusStr == null) {
            throw new IllegalArgumentException("Status is required");
        }
        OrderStatus status = OrderStatus.valueOf(statusStr.toUpperCase());
        StoreOrderResponse response = storeOrderService.updateOrderStatus(id, status);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/approval/material-preview")
    @PreAuthorize("hasAuthority('APPROVE_STORE_ORDER')")
    public ResponseEntity<ApprovalMaterialPreviewResponse> previewApprovalMaterialUsage(
            @Valid @RequestBody ApprovalMaterialPreviewRequest request) {
        return ResponseEntity.ok(storeOrderService.previewApprovalMaterialUsage(request));
    }

    @PatchMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('CREATE_STORE_ORDER')")
    public ResponseEntity<StoreOrderResponse> submitOrder(
            @PathVariable Long id,
            Authentication authentication) {

        StoreOrderResponse response =
                storeOrderService.submitOrder(id, authentication.getName());

        return ResponseEntity.ok(response);
    }


    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CREATE_STORE_ORDER')")
    public ResponseEntity<StoreOrderResponse> updateOrder(
            @PathVariable Long id,
            @Valid @RequestBody StoreOrderRequest request,
            Authentication authentication) {
        StoreOrderResponse response = storeOrderService.updateOrder(id, request, authentication.getName());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CREATE_STORE_ORDER')")
    public ResponseEntity<Void> cancelOrder(@PathVariable Long id, Authentication authentication) {
        storeOrderService.cancelOrder(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/reschedule")
    @PreAuthorize("hasAuthority('APPROVE_STORE_ORDER')")
    public ResponseEntity<StoreOrderResponse> rescheduleOrder(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body) {
        String dateStr = body.get("deliveryDate");
        if (dateStr == null) {
            throw new IllegalArgumentException("deliveryDate is required");
        }
        java.time.LocalDate newDate = java.time.LocalDate.parse(dateStr);
        StoreOrderResponse response = storeOrderService.rescheduleOrder(id, newDate);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/split")
    @PreAuthorize("hasAuthority('APPROVE_STORE_ORDER')")
    public ResponseEntity<java.util.List<StoreOrderResponse>> splitOrder(
            @PathVariable Long id,
            @RequestBody @Valid java.util.List<com.swp.ckms.dto.request.OrderItemRequest> itemsToSplit) {
        java.util.List<StoreOrderResponse> response = storeOrderService.splitOrder(id, itemsToSplit);
        return ResponseEntity.ok(response);
    }
}
