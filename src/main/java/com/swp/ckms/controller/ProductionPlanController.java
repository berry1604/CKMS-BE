package com.swp.ckms.controller;

import com.swp.ckms.dto.request.ProductionPlanRequest;
import com.swp.ckms.dto.response.ProductionPlanResponse;
import com.swp.ckms.service.AllocationService;
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
    private final AllocationService allocationService;

    @PostMapping
    @PreAuthorize("hasAuthority('ORGANIZE_PRODUCTION') or hasAuthority('CREATE_PRODUCTION_PLAN')")
    public ResponseEntity<ProductionPlanResponse> createProductionPlan(
            @Valid @RequestBody ProductionPlanRequest request) {
        
        ProductionPlanResponse response = productionPlanService.createProductionPlan(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}/ready")
    @PreAuthorize("hasAuthority('ORGANIZE_PRODUCTION')")
    public ResponseEntity<ProductionPlanResponse> readyProductionPlan(@PathVariable Long id) {
        ProductionPlanResponse response = productionPlanService.checkAndReadyPlan(id);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('EXECUTE_PRODUCTION')")
    public ResponseEntity<ProductionPlanResponse> startProductionPlan(
            @PathVariable Long id,
            @RequestHeader(value = "If-Match", required = false) Long version) {
        ProductionPlanResponse response = productionPlanService.startProductionPlan(id, version);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/yield")
    @PreAuthorize("hasAuthority('EXECUTE_PRODUCTION')")
    public ResponseEntity<ProductionPlanResponse> reportProductionYield(
            @PathVariable Long id,
            @Valid @RequestBody com.swp.ckms.dto.request.FinishProductionPlanRequest request) {
        
        ProductionPlanResponse response = productionPlanService.reportProductionYield(id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/allocate")
    @PreAuthorize("hasAuthority('ORGANIZE_PRODUCTION')")
    public ResponseEntity<ProductionPlanResponse> confirmAllocation(
            @PathVariable Long id,
            @RequestHeader(value = "If-Match", required = false) Long version) {
        
        ProductionPlanResponse response = allocationService.confirmAllocation(id, version, null);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('ORGANIZE_PRODUCTION')")
    public ResponseEntity<ProductionPlanResponse> cancelProductionPlan(
            @PathVariable Long id,
            @RequestHeader(value = "If-Match", required = false) Long version,
            @RequestParam(name = "returnInventory", defaultValue = "true") boolean returnInventory) {
        ProductionPlanResponse response = productionPlanService.cancelProductionPlan(id, version, returnInventory);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_PRODUCTION_PLAN')")
    public ResponseEntity<org.springframework.data.domain.Page<com.swp.ckms.dto.response.ProductionPlanSummaryResponse>> getAllProductionPlans(
            @RequestParam(required = false) com.swp.ckms.enums.ProductionPlanStatus status,
            org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(productionPlanService.getAllProductionPlans(status, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VIEW_PRODUCTION_PLAN')")
    public ResponseEntity<com.swp.ckms.dto.response.ProductionPlanDetailResponse> getProductionPlanDetail(@PathVariable Long id) {
        return ResponseEntity.ok(productionPlanService.getProductionPlanDetail(id));
    }
}
