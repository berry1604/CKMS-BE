package com.swp.ckms.repository;

import com.swp.ckms.entity.CentralKitchen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CentralKitchenRepository extends JpaRepository<CentralKitchen, Long> {
}
