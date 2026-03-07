package com.swp.ckms.service;

import com.swp.ckms.dto.request.AllocationAdjustmentRequest;
import com.swp.ckms.dto.response.AllocationPreviewResponse;
import com.swp.ckms.dto.response.ProductionPlanResponse;

public interface AllocationService {
    ProductionPlanResponse confirmAllocation(Long planId, Long requestVersion, AllocationAdjustmentRequest adjustmentRequest);
    AllocationPreviewResponse previewAllocation(Long planId);
}
