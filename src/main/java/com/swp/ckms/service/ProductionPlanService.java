package com.swp.ckms.service;

import com.swp.ckms.dto.request.ProductionPlanRequest;
import com.swp.ckms.dto.response.ProductionPlanResponse;

public interface ProductionPlanService {
    ProductionPlanResponse createProductionPlan(ProductionPlanRequest request);
    ProductionPlanResponse checkAndReadyPlan(Long planId);
    ProductionPlanResponse startProductionPlan(Long planId, Long requestVersion);
}
