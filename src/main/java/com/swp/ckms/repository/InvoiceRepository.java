package com.swp.ckms.repository;

import com.swp.ckms.dto.response.InvoiceDetailResponse;
import com.swp.ckms.entity.Invoice;
import com.swp.ckms.enums.InvoiceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    boolean existsByOrder_OrderId(Long orderId);

    List<Invoice> findByStatement_StatementId(Long statementId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Invoice> findByOrder_Store_StoreIdAndStatusInAndIssuedAtBetween(
            Long storeId, List<InvoiceStatus> statuses, java.time.LocalDateTime start, java.time.LocalDateTime end);

    @Query("""
        SELECT new com.swp.ckms.dto.response.InvoiceDetailResponse(
            i.invoiceId, 
            i.order.orderId, 
            i.amount, 
            i.order.orderFee,
            i.order.shippingFee,
            i.issuedAt
        )
        FROM Invoice i 
        WHERE i.statement.statementId = :statementId 
        ORDER BY i.issuedAt ASC
    """)
    List<InvoiceDetailResponse> findInvoiceDetailsByStatementId(@Param("statementId") Long statementId);

    @Modifying
    @Query("""
        UPDATE Invoice i
        SET i.status = :targetStatus
        WHERE i.statement.statementId = :statementId
          AND i.status = com.swp.ckms.enums.InvoiceStatus.IN_STATEMENT
    """)
    int bulkMarkAsPaid(
        @Param("statementId") Long statementId,
        @Param("targetStatus") InvoiceStatus targetStatus
    );
}
