package com.swp.ckms.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionOutputRequest {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Actual produced quantity is required")
    @PositiveOrZero(message = "Quantity must be zero or positive")
    private BigDecimal actualQty;
}
