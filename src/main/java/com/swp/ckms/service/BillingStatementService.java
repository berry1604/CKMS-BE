package com.swp.ckms.service;

import com.swp.ckms.dto.response.BillingStatementResponse;

import java.time.LocalDate;

public interface BillingStatementService {
    BillingStatementResponse generateManualStatement(Long storeId, LocalDate periodStart, LocalDate periodEnd);
}
