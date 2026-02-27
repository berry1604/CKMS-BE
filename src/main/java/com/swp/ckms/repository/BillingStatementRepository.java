package com.swp.ckms.repository;

import com.swp.ckms.entity.BillingStatement;
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
}
