package com.swp.ckms.controller;

import com.swp.ckms.dto.request.KitchenStockImportRequest;
import com.swp.ckms.dto.response.ApiResponse;
import com.swp.ckms.dto.response.KitchenStockItemResponse;
import com.swp.ckms.service.KitchenInventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/kitchen-inventory")
@RequiredArgsConstructor
public class KitchenInventoryController {

    private final KitchenInventoryService inventoryService;

    @GetMapping("/{warehouseId}/stock")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_STAFF')")
    public ResponseEntity<ApiResponse<List<KitchenStockItemResponse>>> getWarehouseStock(@PathVariable Long warehouseId) {
        return ResponseEntity.ok(ApiResponse.<List<KitchenStockItemResponse>>builder()
                .status(HttpStatus.OK.value())
                .message("Warehouse stock retrieved successfully")
                .data(inventoryService.getWarehouseStock(warehouseId))
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PostMapping("/{warehouseId}/import/materials")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
    public ResponseEntity<ApiResponse<List<KitchenStockItemResponse>>> importMaterials(
            @PathVariable Long warehouseId,
            @RequestBody @Valid List<KitchenStockImportRequest> requests) {
        
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<List<KitchenStockItemResponse>>builder()
                .status(HttpStatus.CREATED.value())
                .message("Materials imported successfully")
                .data(inventoryService.importMaterialToKitchen(warehouseId, requests))
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PostMapping("/{warehouseId}/import/products")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
    public ResponseEntity<ApiResponse<List<KitchenStockItemResponse>>> importProducts(
            @PathVariable Long warehouseId,
            @RequestBody @Valid List<KitchenStockImportRequest> requests) {
        
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<List<KitchenStockItemResponse>>builder()
                .status(HttpStatus.CREATED.value())
                .message("Products imported successfully")
                .data(inventoryService.importProductToKitchen(warehouseId, requests))
                .timestamp(LocalDateTime.now())
                .build());
    }
}
