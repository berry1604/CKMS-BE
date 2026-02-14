package com.swp.ckms.dto.request;

import com.swp.ckms.enums.UnitType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MaterialRequest {
    @NotBlank(message = "Material name is required")
    private String name;

    @NotNull(message = "Unit is required")
    private UnitType unit;

    @Min(value = 0, message = "Min stock level cannot be negative")
    private Integer minStockLevel = 0;
}
