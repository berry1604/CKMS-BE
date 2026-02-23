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
}
