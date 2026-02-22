package com.swp.ckms.service;

import com.swp.ckms.dto.request.KitchenStockImportRequest;
import com.swp.ckms.dto.response.KitchenStockItemResponse;

import java.math.BigDecimal;
import java.util.List;

public interface KitchenInventoryService {
    
    List<KitchenStockItemResponse> getWarehouseStock(Long warehouseId);

    List<KitchenStockItemResponse> importMaterialToKitchen(Long warehouseId, List<KitchenStockImportRequest> requests);

    List<KitchenStockItemResponse> importProductToKitchen(Long warehouseId, List<KitchenStockImportRequest> requests);

    BigDecimal getMaterialTotalQuantity(Long warehouseId, Long materialId);

    BigDecimal getProductTotalQuantity(Long warehouseId, Long productId);
}
