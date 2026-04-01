package com.swp.ckms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalMaterialPreviewResponse {

    private Long kitchenId;
    private Integer selectedOrderCount;
    private Integer approvableOrderCount;
    private BigDecimal approvableRatePercent;
    private List<OrderPreviewResult> orderResults;
    private List<MaterialUsagePreview> materials;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderPreviewResult {
        private Long orderId;
        private LocalDate deliveryDate;
        private String status;
        private BigDecimal requestedQty;
        private Boolean approvable;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MaterialUsagePreview {
        private Long materialId;
        private String materialName;
        private String unit;
        private BigDecimal availableQty;
        private BigDecimal requiredQtyForSelected;
        private BigDecimal requiredQtyForApprovable;
        private BigDecimal remainingQty;
        private BigDecimal shortageQty;
    }
}
