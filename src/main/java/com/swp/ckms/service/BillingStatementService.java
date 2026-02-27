package com.swp.ckms.service;

import com.swp.ckms.dto.request.BatchBillingStatementRequest;
import com.swp.ckms.dto.response.BatchBillingStatementResponse;
import com.swp.ckms.dto.response.BillingStatementResponse;

import java.time.LocalDate;

public interface BillingStatementService {
    BillingStatementResponse generateManualStatement(Long storeId, LocalDate periodStart, LocalDate periodEnd);
    BatchBillingStatementResponse generateBatchStatements(BatchBillingStatementRequest request);
}
