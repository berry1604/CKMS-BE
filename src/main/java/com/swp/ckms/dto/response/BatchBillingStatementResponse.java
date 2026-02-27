package com.swp.ckms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchBillingStatementResponse {
    private int totalStoresProcessed;
    private int totalStatementsCreated;
    private int storesSkippedNoInvoices;
    private String status;
}
