package com.swp.ckms.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatementRequest {
    private Long paymentMethodId;
    private String transactionReference;
    private String note;
}
