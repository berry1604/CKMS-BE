package com.swp.ckms.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class KitchenStockItemResponse {
    private Long id;
    private Long warehouseId;
    
    // Distinguish between Material and Product
    private String itemType; // "MATERIAL" or "PRODUCT"
    private Long itemId;
    private String itemName;
    private String unit;

    private BigDecimal quantity;
    private LocalDate expiryDate;
    private Long productionPlanId;
}
