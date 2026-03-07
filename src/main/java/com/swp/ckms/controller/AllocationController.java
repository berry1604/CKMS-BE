package com.swp.ckms.controller;

import com.swp.ckms.dto.request.AllocationAdjustmentRequest;
import com.swp.ckms.dto.response.AllocationPreviewResponse;
import com.swp.ckms.dto.response.ProductionPlanResponse;
import com.swp.ckms.service.AllocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/allocations")
@RequiredArgsConstructor
public class AllocationController {

    private final AllocationService allocationService;

    @PostMapping("/confirm/{productionPlanId}")
    @PreAuthorize("hasAuthority('ORGANIZE_PRODUCTION')")
    public ResponseEntity<ProductionPlanResponse> confirmAllocation(
            @PathVariable Long productionPlanId,
            @RequestHeader(value = "If-Match", required = false) Long version,
            @RequestBody(required = false) AllocationAdjustmentRequest adjustmentRequest) {
        
        ProductionPlanResponse response = allocationService.confirmAllocation(productionPlanId, version, adjustmentRequest);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/preview/{productionPlanId}")
    @PreAuthorize("hasAuthority('ORGANIZE_PRODUCTION')")
    public ResponseEntity<AllocationPreviewResponse> previewAllocation(
            @PathVariable Long productionPlanId) {
        return ResponseEntity.ok(allocationService.previewAllocation(productionPlanId));
    }
}
