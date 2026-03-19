package com.swp.ckms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchSuggestionResponse {
    private java.time.LocalDate targetDate;
    private List<ProductDispatchSuggestion> products;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductDispatchSuggestion {
        private Long productId;
        private String productName;
        private BigDecimal demandQty;
        private BigDecimal kitchenCapacity;
        private BigDecimal ingredientCapacity;
        private BigDecimal suggestedQty;
        private List<OrderAllocationSuggestion> allocations;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderAllocationSuggestion {
        private Long orderId;
        private String storeName;
        private BigDecimal requestedQty;
        private BigDecimal allocatedQty;
        private boolean isFullyAllocated;
    }
}
