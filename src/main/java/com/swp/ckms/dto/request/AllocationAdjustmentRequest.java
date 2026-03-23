package com.swp.ckms.dto.request;

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
public class AllocationAdjustmentRequest {
    private List<OrderItemAdjustment> adjustments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemAdjustment {
        private Long orderId;
        private Long productId;
        private BigDecimal finalQty; 
    }
}
