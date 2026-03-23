package com.swp.ckms.service.impl;

import com.swp.ckms.dto.response.StoreStockItemResponse;
import com.swp.ckms.entity.StoreStockItem;
import com.swp.ckms.repository.StoreStockItemRepository;
import com.swp.ckms.repository.specification.StoreInventorySpecification;
import com.swp.ckms.security.SecurityUtils;
import com.swp.ckms.security.UserContext;
import com.swp.ckms.service.StoreInventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoreInventoryServiceImpl implements StoreInventoryService {

    private final StoreStockItemRepository stockItemRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<StoreStockItemResponse> getStoreInventory(String productName, Long productId, Pageable pageable) {
        UserContext ctx = SecurityUtils.getCurrentUserContext();
        if (ctx == null) {
            throw new AccessDeniedException("Unauthorized access");
        }

        Long storeId = ctx.getStoreId();
        // If not a store user (e.g. system admin), might need different logic or return empty
        // For production ready, we assume store users only or restrict access via controller
        if (storeId == null && !"SYSTEM".equalsIgnoreCase(ctx.getScope())) {
             throw new AccessDeniedException("User is not associated with any store");
        }

        Specification<StoreStockItem> spec = Specification.where(StoreInventorySpecification.hasStoreId(storeId))
                .and(StoreInventorySpecification.hasProductName(productName))
                .and(StoreInventorySpecification.hasProductId(productId));

        if (pageable == null) {
            pageable = Pageable.unpaged();
        }

        return stockItemRepository.findAll(spec, pageable).map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<com.swp.ckms.dto.response.StoreStockBatchResponse> getProductBatches(Long productId) {
        UserContext ctx = SecurityUtils.getCurrentUserContext();
        if (ctx == null || ctx.getStoreId() == null) {
            throw new AccessDeniedException("Unauthorized: No store associated with user");
        }

        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("Invalid Product ID");
        }

        return stockItemRepository.getProductBatches(ctx.getStoreId(), productId);
    }

    private StoreStockItemResponse mapToResponse(StoreStockItem item) {
        return StoreStockItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .unit(item.getProduct().getUnit().name())
                .quantity(item.getQuantity())
                .expiryDate(item.getExpiryDate())
                .productionPlanId(item.getProductionPlan() != null ? item.getProductionPlan().getPlanId() : null)
                .build();
    }
}
