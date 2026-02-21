package com.swp.ckms.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ProductMaterialResponse {
    
    private Long materialId;
    private String materialName;
    private BigDecimal quantity;
    private String unit;
}
