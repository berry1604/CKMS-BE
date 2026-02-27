package com.swp.ckms.controller;

import com.swp.ckms.dto.request.ProductionPlanRequest;
import com.swp.ckms.dto.response.ProductionPlanResponse;
import com.swp.ckms.service.ProductionPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/production-plans")
@RequiredArgsConstructor
public class ProductionPlanController {

    private final ProductionPlanService productionPlanService;

    @PostMapping
    @PreAuthorize("hasRole('COORDINATOR') or hasRole('ADMIN')")
    public ResponseEntity<ProductionPlanResponse> createProductionPlan(
            @Valid @RequestBody ProductionPlanRequest request) {
        
        ProductionPlanResponse response = productionPlanService.createProductionPlan(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}/ready")
    @PreAuthorize("hasRole('COORDINATOR') or hasRole('ADMIN')")
    public ResponseEntity<ProductionPlanResponse> readyProductionPlan(@PathVariable Long id) {
        ProductionPlanResponse response = productionPlanService.checkAndReadyPlan(id);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('COORDINATOR') or hasRole('ADMIN')")
    public ResponseEntity<ProductionPlanResponse> startProductionPlan(
            @PathVariable Long id,
            @RequestHeader(value = "If-Match", required = false) Long version) {
        ProductionPlanResponse response = productionPlanService.startProductionPlan(id, version);
        return ResponseEntity.ok(response);
    }
}
