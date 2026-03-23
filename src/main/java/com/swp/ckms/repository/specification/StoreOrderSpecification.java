package com.swp.ckms.repository.specification;

import com.swp.ckms.entity.StoreOrder;
import com.swp.ckms.enums.OrderStatus;
import org.springframework.data.jpa.domain.Specification;

public class StoreOrderSpecification {

    public static Specification<StoreOrder> hasStatus(OrderStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<StoreOrder> hasStoreId(Long storeId) {
        return (root, query, cb) -> storeId == null ? null : cb.equal(root.get("store").get("storeId"), storeId);
    }
}
