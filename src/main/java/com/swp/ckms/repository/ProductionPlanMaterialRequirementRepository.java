package com.swp.ckms.repository;

import com.swp.ckms.entity.ProductionPlanMaterialRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductionPlanMaterialRequirementRepository extends JpaRepository<ProductionPlanMaterialRequirement, Long> {
    List<ProductionPlanMaterialRequirement> findByPlan_PlanId(Long planId);
}
