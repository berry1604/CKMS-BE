package com.swp.ckms.repository.specification;

import com.swp.ckms.entity.StoreStockItem;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.JoinType;

public class StoreInventorySpecification {

    public static Specification<StoreStockItem> hasStoreId(Long storeId) {
        return (root, query, criteriaBuilder) -> {
            if (storeId == null) return null;
            return criteriaBuilder.equal(
                root.join("warehouse").join("store").get("id"), 
                storeId
            );
        };
    }

    public static Specification<StoreStockItem> hasProductName(String name) {
        return (root, query, criteriaBuilder) -> {
            if (name == null || name.isBlank()) return null;
            return criteriaBuilder.like(
                criteriaBuilder.lower(root.join("product").get("name")), 
                "%" + name.toLowerCase() + "%"
            );
        };
    }

    public static Specification<StoreStockItem> hasProductId(Long productId) {
        return (root, query, criteriaBuilder) -> {
            if (productId == null) return null;
            return criteriaBuilder.equal(root.join("product").get("id"), productId);
        };
    }
}
