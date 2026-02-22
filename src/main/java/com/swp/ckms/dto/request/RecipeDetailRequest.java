package com.swp.ckms.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RecipeDetailRequest {

    @NotNull(message = "Material ID is required")
    private Long materialId;

    @NotNull(message = "Quantity needed is required")
    @Positive(message = "Quantity must be greater than zero")
    private BigDecimal quantityNeeded;
}
