package com.swp.ckms.repository;

import com.swp.ckms.entity.StoreWarehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StoreWarehouseRepository extends JpaRepository<StoreWarehouse, Long> {
    Optional<StoreWarehouse> findByName(String name);
    Optional<StoreWarehouse> findByStore_StoreId(Long storeId);
}
