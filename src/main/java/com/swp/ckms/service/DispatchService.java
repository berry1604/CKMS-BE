package com.swp.ckms.service;

import com.swp.ckms.dto.response.DispatchSuggestionResponse;
import java.time.LocalDate;

public interface DispatchService {
    /**
     * Suggests a production plan for a specific kitchen on a target date.
     * Uses EDD (Earliest Due Date) and quantity-based prioritization.
     */
    DispatchSuggestionResponse suggestProductionPlan(Long kitchenId, LocalDate targetDate);
}
