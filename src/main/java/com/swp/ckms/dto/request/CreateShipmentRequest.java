package com.swp.ckms.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateShipmentRequest {

    @NotNull(message = "Production plan ID is required")
    private Long productionPlanId;

    @NotNull(message = "Store ID is required")
    private Long storeId;

    @NotEmpty(message = "At least one store order is required")
    private List<Long> storeOrderIds;

    private String driverName;
    private String driverPhone;
    private String vehicleInfo;
    private BigDecimal shippingFee;
    private String note;
}