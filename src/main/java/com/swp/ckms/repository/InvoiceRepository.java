package com.swp.ckms.repository;

import com.swp.ckms.entity.Invoice;
import com.swp.ckms.enums.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    boolean existsByOrder_OrderId(Long orderId);

    java.util.List<Invoice> findByOrder_Store_StoreIdAndStatusAndIssuedAtBetween(
            Long storeId, InvoiceStatus status, java.time.LocalDateTime start, java.time.LocalDateTime end);
}
