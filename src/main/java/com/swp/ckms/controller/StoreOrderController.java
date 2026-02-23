package com.swp.ckms.controller;

import com.swp.ckms.dto.request.StoreOrderRequest;
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
    @PreAuthorize("hasAuthority('VIEW_STORE_ORDER')")
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
    @PreAuthorize("hasAuthority('VIEW_STORE_ORDER')")
    public ResponseEntity<Page<StoreOrderResponse>> getAllOrders(
            @RequestParam(defaultValue = "SUBMITTED") OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "orderDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        
        org.springframework.data.domain.Sort sort = sortDir.equalsIgnoreCase("desc") 
                ? org.springframework.data.domain.Sort.by(sortBy).descending() 
                : org.springframework.data.domain.Sort.by(sortBy).ascending();
        Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, sort);
        
        Page<StoreOrderResponse> response = storeOrderService.getAllOrdersByStatus(status, pageable);
        return ResponseEntity.ok(response);
    }
}
