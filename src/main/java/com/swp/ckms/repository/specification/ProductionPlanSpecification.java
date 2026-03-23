package com.swp.ckms.repository.specification;

import com.swp.ckms.entity.ProductionPlan;
import com.swp.ckms.enums.ProductionPlanStatus;
import org.springframework.data.jpa.domain.Specification;

public class ProductionPlanSpecification {

    public static Specification<ProductionPlan> hasStatus(ProductionPlanStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<ProductionPlan> hasKitchenId(Long kitchenId) {
        return (root, query, cb) -> kitchenId == null ? null : cb.equal(root.get("kitchen").get("kitchenId"), kitchenId);
    }
}
