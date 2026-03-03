package com.swp.ckms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingStatementDetailResponse {
    private Long statementId;
    private StoreSimpleResponse store;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal orderTotal;
    private BigDecimal shippingTotal;
    private BigDecimal totalAmount;
    private String status;
    private LocalDateTime paidAt;
    private List<InvoiceDetailResponse> invoices;
}
