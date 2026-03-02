package com.swp.ckms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreStockItemResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String unit;
    private BigDecimal quantity;
    private LocalDate expiryDate;
    private Long productionPlanId;
}
