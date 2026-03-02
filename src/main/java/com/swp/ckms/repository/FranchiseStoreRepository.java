package com.swp.ckms.repository;

import com.swp.ckms.entity.FranchiseStore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FranchiseStoreRepository
        extends JpaRepository<FranchiseStore, Long>,
        JpaSpecificationExecutor<FranchiseStore> {

    Optional<FranchiseStore> findByName(String name);

    boolean existsByName(String name);
}