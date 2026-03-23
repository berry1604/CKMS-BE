package com.swp.ckms.specification;

import com.swp.ckms.entity.User;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class UserSpecification {

    public static Specification<User> filterUsers(
            String role,
            String status,
            String search
    ) {
        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            predicates.add(
                    cb.isTrue(root.get("isActive"))
            );

            if (role != null && !role.isBlank()) {
                predicates.add(
                        cb.equal(root.get("role").get("roleName"), role)
                );
            }

            if (status != null && !status.isBlank()) {
                predicates.add(
                        cb.equal(root.get("status"), status)
                );
            }

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";

                Predicate usernameLike =
                        cb.like(cb.lower(root.get("username")), pattern);

                Predicate emailLike =
                        cb.like(cb.lower(root.get("email")), pattern);

                predicates.add(cb.or(usernameLike, emailLike));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
