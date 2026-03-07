package com.swp.ckms.repository;

import com.swp.ckms.entity.ProductionOutput;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductionOutputRepository extends JpaRepository<ProductionOutput, Long> {
    List<ProductionOutput> findByProductionPlan_PlanId(Long planId);
}
