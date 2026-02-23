package com.swp.ckms.repository;

import com.swp.ckms.entity.KitchenWarehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KitchenWarehouseRepository extends JpaRepository<KitchenWarehouse, Long> {
    Optional<KitchenWarehouse> findByName(String name);
}
