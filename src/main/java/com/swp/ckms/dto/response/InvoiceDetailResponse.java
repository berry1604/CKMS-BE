package com.swp.ckms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDetailResponse {
    private Long invoiceId;
    private Long orderId;
    private BigDecimal amount;
    private BigDecimal orderAmount;
    private BigDecimal shippingAmount;
    private LocalDateTime issuedAt;
}
