package com.swp.ckms.service;

import com.swp.ckms.dto.response.ProductionPlanResponse;

public interface AllocationService {
    ProductionPlanResponse confirmAllocation(Long planId, Long requestVersion);
}
