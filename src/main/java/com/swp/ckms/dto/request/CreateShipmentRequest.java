package com.swp.ckms.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateShipmentRequest {

    @NotNull(message = "AhaMove service ID is required")
    private String ahamoveServiceId; // 
    // Loại dịch vụ AhaMove (ví dụ: "bike", "car", "truck")

    @NotNull(message = "Production plan ID is required")
    private Long productionPlanId;

    @NotEmpty(message = "At least one drop point is required")
    private List<DropPointRequest> dropPoints;

    private String remarks; // Ghi chú chung cho cả shipment

    @Data
    public static class DropPointRequest {
        @NotNull(message = "Store ID is required")
        private Long storeId;

        @NotEmpty(message = "At least one order is required per drop point")
        private List<Long> storeOrderIds;

        private String remarks; // Ghi chú cho điểm giao này
    }
}