package com.swp.ckms.service.impl;

import com.swp.ckms.dto.response.BillingStatementResponse;
import com.swp.ckms.entity.BillingStatement;
import com.swp.ckms.entity.FranchiseStore;
import com.swp.ckms.entity.Invoice;
import com.swp.ckms.enums.InvoiceStatus;
import com.swp.ckms.exception.business.DuplicateResourceException;
import com.swp.ckms.exception.business.ResourceNotFoundException;
import com.swp.ckms.exception.validation.InvalidRequestException;
import com.swp.ckms.repository.BillingStatementRepository;
import com.swp.ckms.repository.FranchiseStoreRepository;
import com.swp.ckms.repository.InvoiceRepository;
import com.swp.ckms.service.BillingStatementService;
import lombok.RequiredArgsConstructor;
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
                .status("UNPAID")
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
                .status(savedStatement.getStatus())
                .invoiceCount(invoices.size())
                .build();
    }
}
