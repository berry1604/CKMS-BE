package com.swp.ckms.service.impl;

import com.swp.ckms.dto.request.KitchenStockImportRequest;
import com.swp.ckms.dto.response.KitchenStockItemResponse;
import com.swp.ckms.entity.KitchenStockItem;
import com.swp.ckms.entity.KitchenWarehouse;
import com.swp.ckms.entity.Material;
import com.swp.ckms.entity.Product;
import com.swp.ckms.entity.ProductionPlan;
import com.swp.ckms.exception.business.ResourceNotFoundException;
import com.swp.ckms.repository.KitchenStockItemRepository;
import com.swp.ckms.repository.KitchenWarehouseRepository;
import com.swp.ckms.repository.MaterialRepository;
import com.swp.ckms.repository.ProductRepository;
import com.swp.ckms.repository.ProductionPlanRepository;
import com.swp.ckms.service.KitchenInventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KitchenInventoryServiceImpl implements KitchenInventoryService {

    private final KitchenStockItemRepository stockItemRepository;
    private final KitchenWarehouseRepository warehouseRepository;
    private final MaterialRepository materialRepository;
    private final ProductRepository productRepository;
    private final ProductionPlanRepository productionPlanRepository;

    @Override
    public List<KitchenStockItemResponse> getWarehouseStock(Long warehouseId) {
        verifyWarehouseExists(warehouseId);
        return stockItemRepository.findByWarehouse_WarehouseId(warehouseId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<KitchenStockItemResponse> importMaterialToKitchen(Long warehouseId, List<KitchenStockImportRequest> requests) {
        KitchenWarehouse warehouse = verifyWarehouseExists(warehouseId);
        List<KitchenStockItem> importedItems = new ArrayList<>();

        for (KitchenStockImportRequest request : requests) {
            Material material = materialRepository.findById(request.getItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Material not found with ID: " + request.getItemId()));

            // Find existing identical batch or create new
            Optional<KitchenStockItem> existingItem = stockItemRepository
                    .findByWarehouse_WarehouseIdAndMaterial_Id(warehouseId, material.getId()).stream()
                    .filter(item -> isSameBatch(item, request))
                    .findFirst();

            KitchenStockItem stockItem;
            if (existingItem.isPresent()) {
                stockItem = existingItem.get();
                stockItem.setQuantity(stockItem.getQuantity().add(request.getQuantity()));
            } else {
                ProductionPlan plan = getProductionPlanIfPresent(request.getProductionPlanId());
                stockItem = KitchenStockItem.builder()
                        .warehouse(warehouse)
                        .material(material)
                        .product(null)
                        .quantity(request.getQuantity())
                        .expiryDate(request.getExpiryDate())
                        .productionPlan(plan)
                        .build();
            }
            importedItems.add(stockItemRepository.save(stockItem));
        }
        
        return importedItems.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<KitchenStockItemResponse> importProductToKitchen(Long warehouseId, List<KitchenStockImportRequest> requests) {
        KitchenWarehouse warehouse = verifyWarehouseExists(warehouseId);
        List<KitchenStockItem> importedItems = new ArrayList<>();

        for (KitchenStockImportRequest request : requests) {
            Product product = productRepository.findById(request.getItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + request.getItemId()));

            Optional<KitchenStockItem> existingItem = stockItemRepository
                    .findByWarehouse_WarehouseIdAndProduct_Id(warehouseId, product.getId()).stream()
                    .filter(item -> isSameBatch(item, request))
                    .findFirst();

            KitchenStockItem stockItem;
            if (existingItem.isPresent()) {
                stockItem = existingItem.get();
                stockItem.setQuantity(stockItem.getQuantity().add(request.getQuantity()));
            } else {
                ProductionPlan plan = getProductionPlanIfPresent(request.getProductionPlanId());
                stockItem = KitchenStockItem.builder()
                        .warehouse(warehouse)
                        .material(null)
                        .product(product)
                        .quantity(request.getQuantity())
                        .expiryDate(request.getExpiryDate())
                        .productionPlan(plan)
                        .build();
            }
            importedItems.add(stockItemRepository.save(stockItem));
        }
        
        return importedItems.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    public BigDecimal getMaterialTotalQuantity(Long warehouseId, Long materialId) {
        verifyWarehouseExists(warehouseId);
        BigDecimal total = stockItemRepository.sumMaterialQuantityByWarehouseId(warehouseId, materialId);
        return total != null ? total : BigDecimal.ZERO;
    }

    @Override
    public BigDecimal getProductTotalQuantity(Long warehouseId, Long productId) {
        verifyWarehouseExists(warehouseId);
        BigDecimal total = stockItemRepository.sumProductQuantityByWarehouseId(warehouseId, productId);
        return total != null ? total : BigDecimal.ZERO;
    }

    // --- Private Helper Methods ---

    private KitchenWarehouse verifyWarehouseExists(Long warehouseId) {
        return warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Kitchen Warehouse not found: " + warehouseId));
    }

    private ProductionPlan getProductionPlanIfPresent(Long planId) {
        if (planId == null) return null;
        return productionPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Production Plan not found: " + planId));
    }

    private boolean isSameBatch(KitchenStockItem item, KitchenStockImportRequest request) {
        boolean sameExpiry = (item.getExpiryDate() == null && request.getExpiryDate() == null) ||
                             (item.getExpiryDate() != null && item.getExpiryDate().equals(request.getExpiryDate()));
        boolean samePlan = (item.getProductionPlan() == null && request.getProductionPlanId() == null) ||
                           (item.getProductionPlan() != null && item.getProductionPlan().getPlanId().equals(request.getProductionPlanId()));
        return sameExpiry && samePlan;
    }

    private KitchenStockItemResponse mapToResponse(KitchenStockItem item) {
        boolean isMaterial = item.getMaterial() != null;
        
        String itemName = isMaterial ? item.getMaterial().getName() : item.getProduct().getName();
        String itemType = isMaterial ? "MATERIAL" : "PRODUCT";
        Long itemId = isMaterial ? item.getMaterial().getId() : item.getProduct().getId();
        String unit = isMaterial ? item.getMaterial().getUnit().name() : item.getProduct().getUnit().name();
        Long planId = item.getProductionPlan() != null ? item.getProductionPlan().getPlanId() : null;

        return KitchenStockItemResponse.builder()
                .id(item.getId())
                .warehouseId(item.getWarehouse().getWarehouseId())
                .itemType(itemType)
                .itemId(itemId)
                .itemName(itemName)
                .unit(unit)
                .quantity(item.getQuantity())
                .expiryDate(item.getExpiryDate())
                .productionPlanId(planId)
                .build();
    }
}
