package com.swp.ckms.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class RecipeDetailResponse {
    private Long id;
    private Long materialId;
    private String materialName;
    private String materialUnit;
    private BigDecimal quantityNeeded;
}
