package com.swp.ckms.repository;

import com.swp.ckms.dto.response.BillingStatementSummaryResponse;
import com.swp.ckms.entity.BillingStatement;
import com.swp.ckms.enums.BillingStatementStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface BillingStatementRepository extends JpaRepository<BillingStatement, Long> {

    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN TRUE ELSE FALSE END FROM BillingStatement b " +
           "WHERE b.store.storeId = :storeId " +
           "AND b.cycleStart <= :end AND b.cycleEnd >= :start")
    boolean existsOverlappingStatement(@Param("storeId") Long storeId, 
                                       @Param("start") LocalDate start, 
                                       @Param("end") LocalDate end);

    java.util.Optional<BillingStatement> findByStatementIdAndStore_StoreId(Long statementId, Long storeId);

    @Query("""
            SELECT new com.swp.ckms.dto.response.BillingStatementSummaryResponse(
                b.statementId,
                CONCAT('Kỳ T', DATE_FORMAT(b.cycleStart, '%m/%Y')),
                b.totalAmount,
                CAST(b.status AS string),
                b.issuedAt
            )
            FROM BillingStatement b
            WHERE (:storeId IS NULL OR b.store.storeId = :storeId)
            AND (:status IS NULL OR b.status = :status)
            """)
    Page<BillingStatementSummaryResponse> findSummary(
            @Param("storeId") Long storeId,
            @Param("status") BillingStatementStatus status,
            Pageable pageable);
}
