package com.swp.ckms.service.impl;

import com.swp.ckms.dto.request.BatchBillingStatementRequest;
import com.swp.ckms.dto.request.PaymentStatementRequest;
import com.swp.ckms.dto.response.BatchBillingStatementResponse;
import com.swp.ckms.dto.response.BillingStatementDetailResponse;
import com.swp.ckms.dto.response.BillingStatementResponse;
import com.swp.ckms.dto.response.BillingStatementSummaryResponse;
import com.swp.ckms.dto.response.InvoiceDetailResponse;
import com.swp.ckms.dto.response.PaymentStatementResponse;
import com.swp.ckms.dto.response.StoreSimpleResponse;
import com.swp.ckms.entity.BillingStatement;
import com.swp.ckms.entity.FranchiseStore;
import com.swp.ckms.entity.Invoice;
import com.swp.ckms.entity.PaymentMethod;
import com.swp.ckms.enums.BillingStatementStatus;
import com.swp.ckms.enums.InvoiceStatus;
import com.swp.ckms.exception.business.DuplicateResourceException;
import com.swp.ckms.exception.business.ForbiddenException;
import com.swp.ckms.exception.business.ResourceNotFoundException;
import com.swp.ckms.exception.validation.InvalidRequestException;
import com.swp.ckms.integration.payment.PaymentGateway;
import com.swp.ckms.repository.BillingStatementRepository;
import com.swp.ckms.repository.FranchiseStoreRepository;
import com.swp.ckms.repository.InvoiceRepository;
import com.swp.ckms.repository.PaymentMethodRepository;
import com.swp.ckms.security.SecurityUtils;
import com.swp.ckms.security.UserContext;
import com.swp.ckms.service.BillingStatementService;
import com.swp.ckms.util.StoreOrderAmountUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingStatementServiceImpl implements BillingStatementService {

    private final BillingStatementRepository billingStatementRepository;
    private final InvoiceRepository invoiceRepository;
    private final FranchiseStoreRepository franchiseStoreRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentGateway paymentGateway;
    private final com.swp.ckms.service.NotificationService notificationService;
    private final com.swp.ckms.util.RecipientResolver recipientResolver;

    @Override
    @Transactional
    public BillingStatementResponse generateManualStatement(Long storeId, LocalDate periodStart, LocalDate periodEnd) {
        if (periodStart.isAfter(periodEnd)) {
            throw new InvalidRequestException("periodStart must be before or equal to periodEnd");
        }

        FranchiseStore store = franchiseStoreRepository.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("FranchiseStore not found with id: " + storeId));

        boolean overlaps = billingStatementRepository.existsOverlappingStatement(storeId, periodStart, periodEnd);
        if (overlaps) {
            throw new DuplicateResourceException("A BillingStatement already exists for this store in the given period");
        }

        LocalDateTime startDateTime = periodStart.atStartOfDay();
        LocalDateTime endDateTime = periodEnd.atTime(LocalTime.MAX);

        List<InvoiceStatus> targetStatuses = List.of(InvoiceStatus.PENDING, InvoiceStatus.FULFILLED);
        List<Invoice> invoices = invoiceRepository.findByOrder_Store_StoreIdAndStatusInAndIssuedAtBetween(
                storeId, targetStatuses, startDateTime, endDateTime);

        BigDecimal orderTotal = invoices.stream()
            .map(this::extractOrderFee)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal shippingTotal = invoices.stream()
            .map(this::extractShippingFee)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAmount = orderTotal.add(shippingTotal);

        BillingStatement statement = BillingStatement.builder()
                .store(store)
                .cycleStart(periodStart)
                .cycleEnd(periodEnd)
                .orderTotal(orderTotal)
                .shippingTotal(shippingTotal)
                .totalAmount(totalAmount)
                .status(BillingStatementStatus.ISSUED)
                .build();

        BillingStatement savedStatement = billingStatementRepository.save(statement);

        if (!invoices.isEmpty()) {
            for (Invoice invoice : invoices) {
                invoice.setStatus(InvoiceStatus.IN_STATEMENT);
                invoice.setStatement(savedStatement);
            }
            invoiceRepository.saveAll(invoices);
        }

        String cycleMonth = periodStart.format(DateTimeFormatter.ofPattern("MM/yyyy"));
        String cycleName = "Kỳ T" + cycleMonth;

        BillingStatementResponse response = BillingStatementResponse.builder()
                .statementId(savedStatement.getStatementId())
                .storeId(storeId)
                .cycleName(cycleName)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .orderTotal(orderTotal)
                .shippingTotal(shippingTotal)
                .totalAmount(totalAmount)
                .status(savedStatement.getStatus().name())
                .invoiceCount(invoices.size())
                .build();

        // --- TRIGGER NOTIFICATION ---
        try {
            String recipient = recipientResolver.resolveStoreManagerEmail(storeId, null);
            if (recipient != null) {
                java.util.Map<String, Object> payload = new java.util.HashMap<>();
                payload.put("statementId", savedStatement.getStatementId());
                payload.put("storeName", store.getName());
                payload.put("orderTotal", orderTotal);
                payload.put("shippingTotal", shippingTotal);
                payload.put("totalAmount", totalAmount);
                payload.put("issuedDate", LocalDate.now().toString());
                payload.put("dueDate", LocalDate.now().plusDays(7).toString());
                payload.put("paymentLink", "http://localhost:5173/billing/pay/" + savedStatement.getStatementId());

                notificationService.createEmailNotification(
                        com.swp.ckms.enums.NotificationType.BILLING_STATEMENT_CREATED,
                        recipient,
                        "billing-statement.html",
                        payload,
                        "BILLING_ISSUE_" + savedStatement.getStatementId()
                );
            }
        } catch (Exception e) {
            log.error("Failed to trigger billing statement notification", e);
        }

        return response;
    }

    @Override
    @Transactional
    public BatchBillingStatementResponse generateBatchStatements(BatchBillingStatementRequest request) {
        LocalDate periodStart = request.getPeriodStart();
        LocalDate periodEnd = request.getPeriodEnd();
        
        if (periodStart.isAfter(periodEnd)) {
            throw new InvalidRequestException("periodStart must be before or equal to periodEnd");
        }

        List<FranchiseStore> allStores = franchiseStoreRepository.findAll();
        
        int totalStoresProcessed = allStores.size();
        int totalStatementsCreated = 0;
        int storesSkippedNoInvoices = 0;

        for (FranchiseStore store : allStores) {
            Long storeId = store.getStoreId();
            try {
                // Determine if statement already exists. Skip if true to avoid breaking the batch.
                if (billingStatementRepository.existsOverlappingStatement(storeId, periodStart, periodEnd)) {
                    log.info("Batch: Skipping store {} due to overlapping statement in period.", storeId);
                    continue;
                }

                LocalDateTime startDateTime = periodStart.atStartOfDay();
                LocalDateTime endDateTime = periodEnd.atTime(LocalTime.MAX);

                List<InvoiceStatus> targetStatuses = List.of(InvoiceStatus.PENDING, InvoiceStatus.FULFILLED);
                // Pesimistic lock is handled by InvoiceRepository method
                List<Invoice> invoices = invoiceRepository.findByOrder_Store_StoreIdAndStatusInAndIssuedAtBetween(
                        storeId, targetStatuses, startDateTime, endDateTime);

                if (invoices.isEmpty()) {
                    storesSkippedNoInvoices++;
                    continue;
                }

                BigDecimal orderTotal = invoices.stream()
                    .map(this::extractOrderFee)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal shippingTotal = invoices.stream()
                    .map(this::extractShippingFee)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal totalAmount = orderTotal.add(shippingTotal);

                BillingStatement statement = BillingStatement.builder()
                        .store(store)
                        .cycleStart(periodStart)
                        .cycleEnd(periodEnd)
                        .orderTotal(orderTotal)
                        .shippingTotal(shippingTotal)
                        .totalAmount(totalAmount)
                        .status(BillingStatementStatus.ISSUED)
                        .build();

                BillingStatement savedStatement = billingStatementRepository.save(statement);

                for (Invoice invoice : invoices) {
                    invoice.setStatus(InvoiceStatus.IN_STATEMENT);
                    invoice.setStatement(savedStatement);
                }
                invoiceRepository.saveAll(invoices);
                
                totalStatementsCreated++;
                
                // --- TRIGGER NOTIFICATION (BATCH) ---
                try {
                    String recipient = recipientResolver.resolveStoreManagerEmail(storeId, null);
                    if (recipient != null) {
                        java.util.Map<String, Object> payload = java.util.Map.of(
                                "statementId", savedStatement.getStatementId(),
                                "storeName", store.getName(),
                                "totalAmount", totalAmount,
                                "paymentLink", "http://localhost:5173/billing/pay/" + savedStatement.getStatementId()
                        );
                        notificationService.createEmailNotification(
                                com.swp.ckms.enums.NotificationType.BILLING_STATEMENT_CREATED,
                                recipient,
                                "billing-statement.html",
                                payload,
                                "BILLING_ISSUE_" + savedStatement.getStatementId()
                        );
                    }
                } catch (Exception e) {
                    log.error("Failed to trigger batch billing notification for store {}", storeId);
                }

            } catch (Exception e) {
                log.error("Batch error processing storeId {}: {}", storeId, e.getMessage(), e);
            }
        }

        return BatchBillingStatementResponse.builder()
                .totalStoresProcessed(totalStoresProcessed)
                .totalStatementsCreated(totalStatementsCreated)
                .storesSkippedNoInvoices(storesSkippedNoInvoices)
                .status("COMPLETED")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BillingStatementSummaryResponse> getStatements(Long storeId, BillingStatementStatus status, Pageable pageable) {
        UserContext userContext = SecurityUtils.getCurrentUserContext();
        if (userContext == null) {
            throw new ForbiddenException("Authentication required");
        }

        Long finalStoreIdFilter = storeId;
        if ("STORE".equalsIgnoreCase(userContext.getScope())) {
            Long userStoreId = userContext.getStoreId();
            if (userStoreId == null) {
                throw new ForbiddenException("Staff does not belong to any store");
            }
            // Strict logic: If staff tries to query a store they don't own, immediately throw 403 Forbidden.
            if (storeId != null && !storeId.equals(userStoreId)) {
                throw new ForbiddenException("You cannot access statements of other stores");
            }
            finalStoreIdFilter = userStoreId;
        }

        return billingStatementRepository.findSummary(finalStoreIdFilter, status, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public BillingStatementDetailResponse getStatementById(Long id) {
        UserContext userContext = SecurityUtils.getCurrentUserContext();
        if (userContext == null) {
            throw new ForbiddenException("Authentication required");
        }

        BillingStatement billingStatement;
        if ("STORE".equalsIgnoreCase(userContext.getScope())) {
            Long userStoreId = userContext.getStoreId();
            billingStatement = billingStatementRepository.findByStatementIdAndStore_StoreId(id, userStoreId)
                    .orElseThrow(() -> new ResourceNotFoundException("Billing statement not found or you don't have permission"));
        } else {
            billingStatement = billingStatementRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Billing statement not found"));
        }

        List<InvoiceDetailResponse> invoiceDetails = invoiceRepository.findInvoiceDetailsByStatementId(id);

        if (billingStatement.getStore() == null) {
            throw new ResourceNotFoundException("Store not associated with this billing statement");
        }

        StoreSimpleResponse storeSimple = StoreSimpleResponse.builder()
                .id(billingStatement.getStore().getStoreId())
                .name(billingStatement.getStore().getName())
                .build();

        return BillingStatementDetailResponse.builder()
                .statementId(billingStatement.getStatementId())
                .store(storeSimple)
                .periodStart(billingStatement.getCycleStart())
                .periodEnd(billingStatement.getCycleEnd())
                .orderTotal(billingStatement.getOrderTotal())
                .shippingTotal(billingStatement.getShippingTotal())
                .totalAmount(billingStatement.getTotalAmount())
                .status(billingStatement.getStatus().name())
                .paidAt(billingStatement.getPaidAt())
                .invoices(invoiceDetails)
                .build();
    }

    @Override
    @Transactional
    public PaymentStatementResponse payStatement(Long id, PaymentStatementRequest request) {
        BillingStatement statement = billingStatementRepository.findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Billing statement not found"));

        if (statement.getStatus() == BillingStatementStatus.PAID) {
            // Idempotency check: Immediately return state without Exception on consecutive requests
            return PaymentStatementResponse.builder()
                    .statementId(statement.getStatementId())
                    .status(statement.getStatus().name())
                    .paidAt(statement.getPaidAt())
                    .transactionReference(statement.getTransactionReference())
                    .build();
        }

        if (statement.getStatus() != BillingStatementStatus.ISSUED && statement.getStatus() != BillingStatementStatus.OVERDUE) {
            throw new InvalidRequestException("Only ISSUED or OVERDUE statements can be paid.");
        }

        PaymentMethod method = null;
        if (request.getPaymentMethodId() != null) {
            method = paymentMethodRepository.findById(request.getPaymentMethodId())
                    .orElseThrow(() -> new ResourceNotFoundException("Payment Method not found"));
        }

        statement.setStatus(BillingStatementStatus.PAID);
        statement.setPaidAt(LocalDateTime.now());
        statement.setTransactionReference(request.getTransactionReference());
        statement.setNote(request.getNote());
        statement.setPaymentMethod(method);

        int updatedCount = invoiceRepository.bulkMarkAsPaid(id, InvoiceStatus.PAID);
        log.info("Updated {} invoices to PAID for statement {}", updatedCount, id);

        return PaymentStatementResponse.builder()
                .statementId(statement.getStatementId())
                .status(statement.getStatus().name())
                .paidAt(statement.getPaidAt())
                .transactionReference(statement.getTransactionReference())
                .build();
    }

    @Override
    @Transactional
    public void deleteStatement(Long id) {
        BillingStatement statement = billingStatementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Billing statement not found"));

        if (statement.getStatus() == BillingStatementStatus.PAID) {
            throw new InvalidRequestException("Cannot delete a PAID billing statement");
        }

        // Release associated invoices
        List<Invoice> invoices = invoiceRepository.findByStatement_StatementId(id);
        for (Invoice invoice : invoices) {
            invoice.setStatus(InvoiceStatus.FULFILLED); // Revert to fulfilled so it can be picked up again
            invoice.setStatement(null);
        }
        invoiceRepository.saveAll(invoices);

        billingStatementRepository.delete(statement);
        log.info("Deleted Billing Statement #{} and released {} invoices", id, invoices.size());
    }

    @Transactional(readOnly = true)
    public String createVnPayUrl(Long statementId, String clientIp) {

        BillingStatement statement = billingStatementRepository.findById(statementId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Billing statement not found with id: " + statementId)
                );
        UserContext ctx = SecurityUtils.getCurrentUserContext();
        if (ctx == null) {
            throw new ForbiddenException("Authentication required");
        }


        if ("STORE".equalsIgnoreCase(ctx.getScope())) {
            if (!statement.getStore().getStoreId().equals(ctx.getStoreId())) {
                throw new ForbiddenException("You cannot pay other store's statement");
            }
        }

        if (statement.getStatus() != BillingStatementStatus.ISSUED &&
                statement.getStatus() != BillingStatementStatus.OVERDUE) {
            throw new InvalidRequestException("Only ISSUED or OVERDUE statements can be paid");
        }

        return paymentGateway.createPaymentUrl(
                statement.getStatementId(),
                statement.getTotalAmount(),
                clientIp
        );
    }

    private BigDecimal extractOrderFee(Invoice invoice) {
        if (invoice == null || invoice.getOrder() == null) {
            return BigDecimal.ZERO;
        }
        return StoreOrderAmountUtils.zeroIfNull(invoice.getOrder().getOrderFee());
    }

    private BigDecimal extractShippingFee(Invoice invoice) {
        if (invoice == null || invoice.getOrder() == null) {
            return BigDecimal.ZERO;
        }
        return StoreOrderAmountUtils.zeroIfNull(invoice.getOrder().getShippingFee());
    }
    @Override
    @Transactional
    public void handleVnPayReturn(Map<String, String> params) {

        if (!paymentGateway.verifySignature(params)) {
            throw new InvalidRequestException("Invalid VNPay signature");
        }

        String txnRef = params.get("vnp_TxnRef");
        if (txnRef == null) {
            throw new InvalidRequestException("Missing transaction reference");
        }

        String statementIdStr = txnRef.split("_")[0];

        Long statementId;
        try {
            statementId = Long.parseLong(statementIdStr);
        } catch (NumberFormatException e) {
            throw new InvalidRequestException("Invalid transaction reference format");
        }

        BillingStatement statement = billingStatementRepository.findForUpdate(statementId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Billing statement not found with id: " + statementId)
                );

        if (statement.getStatus() == BillingStatementStatus.PAID) {
            return;
        }

        if (statement.getStatus() != BillingStatementStatus.ISSUED &&
                statement.getStatus() != BillingStatementStatus.OVERDUE) {
            throw new InvalidRequestException("Invalid billing statement state for payment");
        }

        if (!paymentGateway.isPaymentSuccessful(params)) {
            log.warn("VNPay payment failed for statement {}", statementId);
            return;
        }

        BigDecimal paidAmount = new BigDecimal(params.get("vnp_Amount"))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        if (paidAmount.compareTo(statement.getTotalAmount()) != 0) {
            throw new InvalidRequestException("Payment amount mismatch");
        }

        statement.setStatus(BillingStatementStatus.PAID);
        statement.setPaidAt(LocalDateTime.now());
        statement.setTransactionReference(
                paymentGateway.getTransactionReference(params)
        );

        invoiceRepository.bulkMarkAsPaid(statement.getStatementId(), InvoiceStatus.PAID);

        log.info("VNPay payment success for statement {}", statementId);
    }

    @Override
    @Transactional
    public Map<String, String> handleVnPayIpn(Map<String, String> params) {
        log.info("Received IPN request from VNPay: {}", params);
        Map<String, String> response = new java.util.HashMap<>();

        try {
            if (!paymentGateway.verifySignature(params)) {
                response.put("RspCode", "97");
                response.put("Message", "Invalid Checksum");
                return response;
            }

            String txnRef = params.get("vnp_TxnRef");
            if (txnRef == null) {
                response.put("RspCode", "99");
                response.put("Message", "Missing transaction reference");
                return response;
            }

            String statementIdStr = txnRef.split("_")[0];
            Long statementId;
            try {
                statementId = Long.parseLong(statementIdStr);
            } catch (NumberFormatException e) {
                response.put("RspCode", "99");
                response.put("Message", "Invalid transaction reference format");
                return response;
            }

            BillingStatement statement = billingStatementRepository.findForUpdate(statementId).orElse(null);
            if (statement == null) {
                response.put("RspCode", "01");
                response.put("Message", "Order not found");
                return response;
            }

            if (statement.getStatus() == BillingStatementStatus.PAID) {
                response.put("RspCode", "02");
                response.put("Message", "Order already confirmed");
                return response;
            }

            if (statement.getStatus() != BillingStatementStatus.ISSUED &&
                    statement.getStatus() != BillingStatementStatus.OVERDUE) {
                response.put("RspCode", "02");
                response.put("Message", "Order already confirmed");
                return response;
            }

            BigDecimal paidAmount = new BigDecimal(params.get("vnp_Amount"))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            if (paidAmount.compareTo(statement.getTotalAmount()) != 0) {
                response.put("RspCode", "04");
                response.put("Message", "Invalid amount");
                return response;
            }

            if (!paymentGateway.isPaymentSuccessful(params)) {
                log.warn("VNPay IPN payment failed for statement {}", statementId);
                response.put("RspCode", "00");
                response.put("Message", "Confirm Success - Transaction Failed at Gateway");
                return response;
            }

            statement.setStatus(BillingStatementStatus.PAID);
            statement.setPaidAt(LocalDateTime.now());
            statement.setTransactionReference(
                    paymentGateway.getTransactionReference(params)
            );

            invoiceRepository.bulkMarkAsPaid(statement.getStatementId(), InvoiceStatus.PAID);

            log.info("VNPay IPN payment success for statement {}", statementId);
            
            response.put("RspCode", "00");
            response.put("Message", "Confirm Success");
            return response;

        } catch (Exception e) {
            log.error("Error processing VNPay IPN", e);
            response.put("RspCode", "99");
            response.put("Message", "Unknown error");
            return response;
        }
    }
}
