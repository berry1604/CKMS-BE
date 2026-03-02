package com.swp.ckms.service.impl;

import com.swp.ckms.dto.request.StoreCreateRequest;
import com.swp.ckms.dto.response.StoreResponse;
import com.swp.ckms.entity.FranchiseStore;
import com.swp.ckms.entity.StoreWarehouse;
import com.swp.ckms.exception.business.DuplicateNameException;
import com.swp.ckms.repository.FranchiseStoreRepository;
import com.swp.ckms.repository.StoreWarehouseRepository;
import com.swp.ckms.service.FranchiseStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class FranchiseStoreServiceImpl implements FranchiseStoreService {

    private final FranchiseStoreRepository storeRepository;
    private final StoreWarehouseRepository warehouseRepository;

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
                .paymentCycle(request.getPaymentCycle() != null ? request.getPaymentCycle() : "MONTHLY")
                .build();

        FranchiseStore savedStore = Objects.requireNonNull(storeRepository.save(store), "Saved store cannot be null");

        // 2. Automatically create StoreWarehouse
        StoreWarehouse warehouse = StoreWarehouse.builder()
                .name("Kho - " + savedStore.getName())
                .store(savedStore)
                .maxCapacity(request.getWarehouseCapacity())
                .build();

        StoreWarehouse savedWarehouse = Objects.requireNonNull(warehouseRepository.save(warehouse), "Saved warehouse cannot be null");

        return mapToResponse(savedStore, savedWarehouse);
    }

    private StoreResponse mapToResponse(FranchiseStore store, StoreWarehouse warehouse) {
        return StoreResponse.builder()
                .storeId(store.getStoreId())
                .name(store.getName())
                .address(store.getAddress())
                .paymentCycle(store.getPaymentCycle())
                .warehouseId(warehouse.getWarehouseId())
                .warehouseCapacity(warehouse.getMaxCapacity())
                .build();
    }
}
