package com.swp.ckms.repository;

import com.swp.ckms.entity.CentralKitchen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import org.springframework.data.jpa.repository.Query;

@Repository
public interface CentralKitchenRepository extends JpaRepository<CentralKitchen, Long> {
    @Query("SELECT SUM(k.maxDailyCapacity) FROM CentralKitchen k")
    BigDecimal sumTotalMaxDailyCapacity();
}
