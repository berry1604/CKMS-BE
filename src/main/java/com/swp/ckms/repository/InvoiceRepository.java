package com.swp.ckms.repository;

import com.swp.ckms.entity.Invoice;
import com.swp.ckms.enums.InvoiceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    boolean existsByOrder_OrderId(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    java.util.List<Invoice> findByOrder_Store_StoreIdAndStatusAndIssuedAtBetween(
            Long storeId, InvoiceStatus status, java.time.LocalDateTime start, java.time.LocalDateTime end);
}
