package com.swp.ckms.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class KitchenStockImportRequest {

    @NotNull(message = "Item ID (Material or Product) is required")
    private Long itemId;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be greater than zero")
    private BigDecimal quantity;

    @Future(message = "Expiry date must be in the future")
    private LocalDate expiryDate;

    private Long productionPlanId; // Optional, if imported from a plan
}
