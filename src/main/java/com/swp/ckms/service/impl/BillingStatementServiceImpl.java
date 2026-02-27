package com.swp.ckms.service.impl;

import com.swp.ckms.dto.request.BatchBillingStatementRequest;
import com.swp.ckms.dto.response.BatchBillingStatementResponse;
import com.swp.ckms.dto.response.BillingStatementDetailResponse;
import com.swp.ckms.dto.response.BillingStatementResponse;
import com.swp.ckms.dto.response.BillingStatementSummaryResponse;
import com.swp.ckms.dto.response.InvoiceDetailResponse;
import com.swp.ckms.dto.response.StoreSimpleResponse;
import com.swp.ckms.entity.BillingStatement;
import com.swp.ckms.entity.FranchiseStore;
import com.swp.ckms.entity.Invoice;
import com.swp.ckms.enums.BillingStatementStatus;
import com.swp.ckms.enums.InvoiceStatus;
import com.swp.ckms.exception.business.DuplicateResourceException;
import com.swp.ckms.exception.business.ForbiddenException;
import com.swp.ckms.exception.business.ResourceNotFoundException;
import com.swp.ckms.exception.validation.InvalidRequestException;
import com.swp.ckms.repository.BillingStatementRepository;
import com.swp.ckms.repository.FranchiseStoreRepository;
import com.swp.ckms.repository.InvoiceRepository;
import com.swp.ckms.security.SecurityUtils;
import com.swp.ckms.security.UserContext;
import com.swp.ckms.service.BillingStatementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingStatementServiceImpl implements BillingStatementService {

    private final BillingStatementRepository billingStatementRepository;
    private final InvoiceRepository invoiceRepository;
    private final FranchiseStoreRepository franchiseStoreRepository;

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

        List<Invoice> invoices = invoiceRepository.findByOrder_Store_StoreIdAndStatusAndIssuedAtBetween(
                storeId, InvoiceStatus.PENDING, startDateTime, endDateTime);

        BigDecimal totalAmount = invoices.stream()
                .map(Invoice::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BillingStatement statement = BillingStatement.builder()
                .store(store)
                .cycleStart(periodStart)
                .cycleEnd(periodEnd)
                .totalAmount(totalAmount)
                .status(BillingStatementStatus.UNPAID)
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

        return BillingStatementResponse.builder()
                .statementId(savedStatement.getStatementId())
                .storeId(storeId)
                .cycleName(cycleName)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .totalAmount(totalAmount)
                .status(savedStatement.getStatus().name())
                .invoiceCount(invoices.size())
                .build();
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

                // Pesimistic lock is handled by InvoiceRepository method
                List<Invoice> invoices = invoiceRepository.findByOrder_Store_StoreIdAndStatusAndIssuedAtBetween(
                        storeId, InvoiceStatus.PENDING, startDateTime, endDateTime);

                if (invoices.isEmpty()) {
                    storesSkippedNoInvoices++;
                    continue;
                }

                BigDecimal totalAmount = invoices.stream()
                        .map(Invoice::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BillingStatement statement = BillingStatement.builder()
                        .store(store)
                        .cycleStart(periodStart)
                        .cycleEnd(periodEnd)
                        .totalAmount(totalAmount)
                        .status(BillingStatementStatus.UNPAID)
                        .build();

                BillingStatement savedStatement = billingStatementRepository.save(statement);

                for (Invoice invoice : invoices) {
                    invoice.setStatus(InvoiceStatus.IN_STATEMENT);
                    invoice.setStatement(savedStatement);
                }
                invoiceRepository.saveAll(invoices);
                
                totalStatementsCreated++;

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
        boolean isStaffStore = userContext.getRoles().contains("ROLE_STAFF_STORE");

        if (isStaffStore) {
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

        boolean isStaffStore = userContext.getRoles().contains("ROLE_STAFF_STORE");
        BillingStatement statement;

        if (isStaffStore) {
            Long userStoreId = userContext.getStoreId();
            // Fast fail with 404 to avoid ID probing
            statement = billingStatementRepository.findByStatementIdAndStore_StoreId(id, userStoreId)
                    .orElseThrow(() -> new ResourceNotFoundException("Billing statement not found or you don't have permission"));
        } else {
            statement = billingStatementRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Billing statement not found"));
        }

        List<InvoiceDetailResponse> invoiceDetails = invoiceRepository.findInvoiceDetailsByStatementId(id);

        StoreSimpleResponse storeSimple = StoreSimpleResponse.builder()
                .id(statement.getStore().getStoreId())
                .name(statement.getStore().getName())
                .build();

        return BillingStatementDetailResponse.builder()
                .statementId(statement.getStatementId())
                .store(storeSimple)
                .periodStart(statement.getCycleStart())
                .periodEnd(statement.getCycleEnd())
                .totalAmount(statement.getTotalAmount())
                .status(statement.getStatus().name())
                .paidAt(statement.getPaidAt())
                .invoices(invoiceDetails)
                .build();
    }
}
