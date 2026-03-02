package com.swp.ckms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentResponse {
    private Long shipmentId;
    private Long storeId;
    private String storeName;
    private Long productionPlanId;
    private String status;
    private String driverName;
    private String driverPhone;
    private String vehicleInfo;
    private BigDecimal shippingFee;
    private String note;
    private Long createdByUserId;
    private String createdByUsername;
    private Long confirmedByUserId;
    private String confirmedByUsername;
    private LocalDateTime createdAt;
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;
    private List<Long> storeOrderIds;
}