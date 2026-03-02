package com.swp.ckms.service;

import com.swp.ckms.dto.response.StoreStockItemResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface StoreInventoryService {
    Page<StoreStockItemResponse> getStoreInventory(String productName, Long productId, Pageable pageable);
}
