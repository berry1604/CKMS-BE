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
public class BillingStatementResponse {
    private Long statementId;
    private Long storeId;
    private String cycleName;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal orderTotal;
    private BigDecimal shippingTotal;
    private BigDecimal totalAmount;
    private String status;
    private int invoiceCount;
}
