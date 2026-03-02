package com.swp.ckms.repository.specification;

import com.swp.ckms.entity.FranchiseStore;
import org.springframework.data.jpa.domain.Specification;

public class StoreSpecification {

    public static Specification<FranchiseStore> searchByName(String search) {
        return (root, query, cb) -> {

            if (search == null || search.isBlank()) {
                return cb.conjunction();
            }

            return cb.like(
                    cb.lower(root.get("name")),
                    "%" + search.toLowerCase() + "%"
            );
        };
    }
}
