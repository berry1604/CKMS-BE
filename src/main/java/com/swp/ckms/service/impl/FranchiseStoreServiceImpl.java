package com.swp.ckms.service.impl;

import com.swp.ckms.dto.request.StoreCreateRequest;
import com.swp.ckms.dto.request.StoreUpdateRequest;
import com.swp.ckms.dto.response.StoreResponse;
import com.swp.ckms.entity.FranchiseStore;
import com.swp.ckms.entity.StoreWarehouse;
import com.swp.ckms.exception.business.DuplicateNameException;
import com.swp.ckms.repository.FranchiseStoreRepository;
import com.swp.ckms.repository.StoreWarehouseRepository;
import com.swp.ckms.repository.specification.StoreSpecification;
import com.swp.ckms.service.FranchiseStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;


import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class FranchiseStoreServiceImpl implements FranchiseStoreService {

    private final FranchiseStoreRepository storeRepository;
    private final StoreWarehouseRepository warehouseRepository;

        @PersistenceContext
        private EntityManager entityManager;

    @Override
    @Transactional
    public StoreResponse createStore(StoreCreateRequest request) {
        if (storeRepository.existsByName(request.getName())) {
            throw new DuplicateNameException("Tên cửa hàng đã tồn tại: " + request.getName());
        }

        // 1. Create FranchiseStore
        FranchiseStore store = FranchiseStore.builder()
                .name(request.getName())
                .address(request.getAddress())
                .phoneNumber(request.getPhoneNumber())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .paymentCycle(request.getPaymentCycle() != null ? request.getPaymentCycle() : "MONTHLY")
                .build();

        FranchiseStore savedStore = Objects.requireNonNull(storeRepository.save(store), "Saved store cannot be null");

        StoreWarehouse warehouse = StoreWarehouse.builder()
                .name("Kho - " + savedStore.getName())
                .store(savedStore)
                .build();

                StoreWarehouse savedWarehouse;
                try {
                        savedWarehouse = Objects.requireNonNull(warehouseRepository.save(warehouse), "Saved warehouse cannot be null");
                } catch (DataIntegrityViolationException ex) {
                        if (!isWarehousePrimaryKeyConflict(ex)) {
                                throw ex;
                        }
                        log.warn("Detected out-of-sync sequence for store_warehouses.warehouse_id, attempting recovery.");
                        realignStoreWarehouseIdSequence();
                        savedWarehouse = Objects.requireNonNull(warehouseRepository.save(warehouse), "Saved warehouse cannot be null");
                }

        return mapToResponse(savedStore, savedWarehouse);
    }

        private boolean isWarehousePrimaryKeyConflict(DataIntegrityViolationException ex) {
                Throwable current = ex;
                while (current != null) {
                        String msg = current.getMessage();
                        if (msg != null && msg.contains("store_warehouses_pkey")) {
                                return true;
                        }
                        current = current.getCause();
                }
                return false;
        }

        private void realignStoreWarehouseIdSequence() {
                entityManager.createNativeQuery(
                                "SELECT setval(pg_get_serial_sequence('store_warehouses','warehouse_id'), " +
                                                "COALESCE((SELECT MAX(warehouse_id) FROM store_warehouses), 1), true)"
                ).getSingleResult();
        }

    private StoreResponse mapToResponse(FranchiseStore store, StoreWarehouse warehouse) {
        return StoreResponse.builder()
                .storeId(store.getStoreId())
                .name(store.getName())
                .address(store.getAddress())
                .phoneNumber(store.getPhoneNumber())
                .latitude(store.getLatitude())
                .longitude(store.getLongitude())
                .paymentCycle(store.getPaymentCycle())
                .warehouseId(warehouse != null ? warehouse.getWarehouseId() : null)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public StoreResponse getStoreById(Long id) {

        FranchiseStore store = storeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Store not found"));

        StoreWarehouse warehouse = warehouseRepository
                .findByStore_StoreId(store.getStoreId())
                .orElseThrow(() -> new RuntimeException("Warehouse not found"));

        return mapToResponse(store, warehouse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StoreResponse> getAllStores(
            int page,
            int size,
            String search
    ) {

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by("storeId").descending()
        );

        Specification<FranchiseStore> spec =
                StoreSpecification.searchByName(search);

        Page<FranchiseStore> stores =
                storeRepository.findAll(spec, pageable);

        return stores.map(store -> {

            StoreWarehouse warehouse = warehouseRepository
                    .findByStore_StoreId(store.getStoreId())
                    .orElse(null);

            return mapToResponse(store, warehouse);
        });
    }

    @Override
    @Transactional
    public StoreResponse updateStore(Long id, StoreUpdateRequest request) {

        FranchiseStore store = storeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Store not found"));


        if (!store.getName().equals(request.getName())
                && storeRepository.existsByName(request.getName())) {
            throw new DuplicateNameException("Tên cửa hàng đã tồn tại: " + request.getName());
        }

        //Update thông tin store
        store.setName(request.getName());
        store.setAddress(request.getAddress());
        store.setPhoneNumber(request.getPhoneNumber());
        store.setLatitude(request.getLatitude());
        store.setLongitude(request.getLongitude());
        store.setPaymentCycle(
                request.getPaymentCycle() != null
                        ? request.getPaymentCycle()
                        : store.getPaymentCycle()
        );

        FranchiseStore updatedStore = storeRepository.save(store);

        //Lấy warehouse tương ứng
        StoreWarehouse warehouse = warehouseRepository
                .findByStore_StoreId(updatedStore.getStoreId())
                .orElseThrow(() -> new RuntimeException("Warehouse not found"));

        return mapToResponse(updatedStore, warehouse);
    }

    @Override
    @Transactional
    public void deleteStore(Long id) {

        FranchiseStore store = storeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Store not found"));

        store.setIsActive(false);

        storeRepository.save(store);
    }
}
