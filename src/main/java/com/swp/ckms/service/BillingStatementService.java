package com.swp.ckms.service;

import com.swp.ckms.dto.request.BatchBillingStatementRequest;
import com.swp.ckms.dto.request.PaymentStatementRequest;
import com.swp.ckms.dto.response.BatchBillingStatementResponse;
import com.swp.ckms.dto.response.BillingStatementDetailResponse;
import com.swp.ckms.dto.response.BillingStatementResponse;
import com.swp.ckms.dto.response.BillingStatementSummaryResponse;
import com.swp.ckms.dto.response.PaymentStatementResponse;
import com.swp.ckms.enums.BillingStatementStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface BillingStatementService {
    BillingStatementResponse generateManualStatement(Long storeId, LocalDate periodStart, LocalDate periodEnd);
    BatchBillingStatementResponse generateBatchStatements(BatchBillingStatementRequest request);
    Page<BillingStatementSummaryResponse> getStatements(Long storeId, BillingStatementStatus status, Pageable pageable);
    BillingStatementDetailResponse getStatementById(Long id);
    PaymentStatementResponse payStatement(Long id, PaymentStatementRequest request);
    
    void deleteStatement(Long id);
}
