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
public class BillingStatementSummaryResponse {
    private Long statementId;
    private String cycleName;
    private BigDecimal totalAmount;
    private String status;
    private LocalDateTime issuedAt;
}
