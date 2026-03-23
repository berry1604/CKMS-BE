package com.swp.ckms.repository;

import com.swp.ckms.entity.Privilege;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public interface PrivilegeRepository extends JpaRepository<Privilege, Long> {

    Optional<Privilege> findByCode(String code);

    boolean existsByCode(String code);

    Set<Privilege> findByPrivilegeIdIn(Set<Long> ids);
}
