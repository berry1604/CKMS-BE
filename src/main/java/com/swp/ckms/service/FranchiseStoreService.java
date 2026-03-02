package com.swp.ckms.service;

import com.swp.ckms.dto.request.StoreCreateRequest;
import com.swp.ckms.dto.request.StoreUpdateRequest;
import com.swp.ckms.dto.response.StoreResponse;

public interface FranchiseStoreService {
    StoreResponse createStore(StoreCreateRequest request);
    StoreResponse getStoreById(Long id);

    org.springframework.data.domain.Page<StoreResponse> getAllStores(
            int page,
            int size,
            String search
    );

    StoreResponse updateStore(Long id, StoreUpdateRequest request);
}
