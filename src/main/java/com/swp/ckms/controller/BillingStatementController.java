package com.swp.ckms.controller;

import com.swp.ckms.dto.request.BatchBillingStatementRequest;
import com.swp.ckms.dto.response.ApiResponse;
import com.swp.ckms.dto.response.BatchBillingStatementResponse;
import com.swp.ckms.dto.response.BillingStatementResponse;
import com.swp.ckms.service.BillingStatementService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/billing-statements")
@RequiredArgsConstructor
public class BillingStatementController {

    private final BillingStatementService billingStatementService;

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<ApiResponse<BillingStatementResponse>> generateManualStatement(
            @RequestParam Long storeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {

        BillingStatementResponse response = billingStatementService.generateManualStatement(storeId, periodStart, periodEnd);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<BillingStatementResponse>builder()
                        .status(HttpStatus.CREATED.value())
                        .message("Billing statement generated successfully")
                        .data(response)
                        .timestamp(java.time.LocalDateTime.now())
                        .build());
    }

    @PostMapping("/generate/batch")
    @PreAuthorize("hasRole('SYSTEM')")
    public ResponseEntity<ApiResponse<BatchBillingStatementResponse>> generateBatchStatements(
            @RequestBody BatchBillingStatementRequest request) {

        BatchBillingStatementResponse response = billingStatementService.generateBatchStatements(request);

        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.<BatchBillingStatementResponse>builder()
                        .status(HttpStatus.OK.value())
                        .message("Batch billing statements execution completed")
                        .data(response)
                        .timestamp(java.time.LocalDateTime.now())
                        .build());
    }
}
