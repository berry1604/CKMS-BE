package com.swp.ckms.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AddMaterialRequest {

    @NotNull(message = "Material ID is required")
    private Long materialId;

    @NotNull(message = "Quantity is required")
    @Min(value = 0, message = "Quantity must be greater than 0")
    private BigDecimal quantity;
    
    private String unit;
}
