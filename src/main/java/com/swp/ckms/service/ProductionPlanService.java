package com.swp.ckms.service;

import com.swp.ckms.dto.request.ProductionPlanRequest;
import com.swp.ckms.dto.response.ProductionPlanResponse;

public interface ProductionPlanService {
    ProductionPlanResponse createProductionPlan(ProductionPlanRequest request);
    ProductionPlanResponse checkAndReadyPlan(Long planId);
    ProductionPlanResponse startProductionPlan(Long planId, Long requestVersion);
    ProductionPlanResponse reportProductionYield(Long planId, com.swp.ckms.dto.request.FinishProductionPlanRequest request);
    ProductionPlanResponse cancelProductionPlan(Long planId, Long requestVersion, boolean returnInventory);

    org.springframework.data.domain.Page<com.swp.ckms.dto.response.ProductionPlanSummaryResponse> getAllProductionPlans(
            com.swp.ckms.enums.ProductionPlanStatus status, org.springframework.data.domain.Pageable pageable);

    com.swp.ckms.dto.response.ProductionPlanDetailResponse getProductionPlanDetail(Long planId);
}
