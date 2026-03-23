package com.swp.ckms.repository.specification;

import com.swp.ckms.entity.FranchiseStore;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

public class StoreSpecification {

    public static Specification<FranchiseStore> searchByName(String search) {
        return (root, query, cb) -> {

            Predicate activePredicate =
                    cb.isTrue(root.get("isActive"));

            if (search == null || search.isBlank()) {
                return activePredicate;
            }

            Predicate searchPredicate =
                    cb.like(
                            cb.lower(root.get("name")),
                            "%" + search.toLowerCase() + "%"
                    );

            return cb.and(activePredicate, searchPredicate);
        };
    }
}
