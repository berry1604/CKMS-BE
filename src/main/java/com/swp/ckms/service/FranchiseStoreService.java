package com.swp.ckms.service;

import com.swp.ckms.dto.request.StoreCreateRequest;
import com.swp.ckms.dto.response.StoreResponse;

public interface FranchiseStoreService {
    StoreResponse createStore(StoreCreateRequest request);
}
