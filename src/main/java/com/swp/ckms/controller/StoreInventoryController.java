package com.swp.ckms.controller;

import com.swp.ckms.dto.response.ApiResponse;
import com.swp.ckms.dto.response.StoreStockItemResponse;
import com.swp.ckms.service.StoreInventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/store-inventory")
@RequiredArgsConstructor
public class StoreInventoryController {

    private final StoreInventoryService storeInventoryService;

    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_STORE_INVENTORY')")
    public ApiResponse<Page<StoreStockItemResponse>> getStoreInventory(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long productId,
            @PageableDefault(size = 20, sort = "expiryDate", direction = Sort.Direction.ASC) Pageable pageable) {
        
        // Safety cap for page size as per production-ready plan
        if (pageable.getPageSize() > 50) {
            pageable = org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), 50, pageable.getSort());
        }

        Page<StoreStockItemResponse> response = storeInventoryService.getStoreInventory(name, productId, pageable);
        return ApiResponse.success("Tải danh sách tồn kho thành công", response);
    }
}
