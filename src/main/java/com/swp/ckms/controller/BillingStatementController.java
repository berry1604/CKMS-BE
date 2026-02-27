package com.swp.ckms.controller;

import com.swp.ckms.dto.request.BatchBillingStatementRequest;
import com.swp.ckms.dto.request.PaymentStatementRequest;
import com.swp.ckms.dto.response.ApiResponse;
import com.swp.ckms.dto.response.BatchBillingStatementResponse;
import com.swp.ckms.dto.response.BillingStatementDetailResponse;
import com.swp.ckms.dto.response.BillingStatementResponse;
import com.swp.ckms.dto.response.BillingStatementSummaryResponse;
import com.swp.ckms.dto.response.PaymentStatementResponse;
import com.swp.ckms.enums.BillingStatementStatus;
import com.swp.ckms.service.BillingStatementService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
    @PreAuthorize("hasRole('ADMIN')")
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
    @PreAuthorize("hasRole('ADMIN')")
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

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'STORE_STAFF')")
    public ResponseEntity<ApiResponse<Page<BillingStatementSummaryResponse>>> getStatements(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) BillingStatementStatus status,
            @PageableDefault(sort = "issuedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<BillingStatementSummaryResponse> response = billingStatementService.getStatements(storeId, status, pageable);

        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.<Page<BillingStatementSummaryResponse>>builder()
                        .status(HttpStatus.OK.value())
                        .message("Statements fetched successfully")
                        .data(response)
                        .timestamp(java.time.LocalDateTime.now())
                        .build());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'STORE_STAFF')")
    public ResponseEntity<ApiResponse<BillingStatementDetailResponse>> getStatementDetail(@PathVariable Long id) {
        BillingStatementDetailResponse response = billingStatementService.getStatementById(id);
        
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.<BillingStatementDetailResponse>builder()
                        .status(HttpStatus.OK.value())
                        .message("Statement detailed information fetched successfully")
                        .data(response)
                        .timestamp(java.time.LocalDateTime.now())
                        .build());
    }

    @PatchMapping("/{id}/pay")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PaymentStatementResponse>> payStatement(
            @PathVariable Long id,
            @RequestBody PaymentStatementRequest request) {
        
        PaymentStatementResponse response = billingStatementService.payStatement(id, request);
        
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.<PaymentStatementResponse>builder()
                        .status(HttpStatus.OK.value())
                        .message("Statement payment successfully processed")
                        .data(response)
                        .timestamp(java.time.LocalDateTime.now())
                        .build());
    }
}
