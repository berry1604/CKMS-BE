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
public class StoreStockBatchResponse {
    private Long id;
    private Long productId;
    private String productName;
    private BigDecimal quantity;
    private LocalDate expiryDate;
    private Long productionPlanId;
    private String batchCode; // Added as per senior review recommendation

    public StoreStockBatchResponse(Long id, Long productId, String productName, BigDecimal quantity, 
                                 LocalDate expiryDate, Long productionPlanId, String batchCode) {
        this.id = id;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.expiryDate = expiryDate;
        this.productionPlanId = productionPlanId;
        this.batchCode = batchCode;
    }
}
