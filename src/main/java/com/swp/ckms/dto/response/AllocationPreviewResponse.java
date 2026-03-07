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
public class AllocationPreviewResponse {
    private Long planId;
    private List<ProposedOrderAllocation> orders;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProposedOrderAllocation {
        private Long orderId;
        private String storeName;
        private List<ProposedItemAllocation> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProposedItemAllocation {
        private Long productId;
        private String productName;
        private BigDecimal requestedQty;
        private BigDecimal proposedQty; 
    }
}
