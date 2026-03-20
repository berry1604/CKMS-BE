package com.swp.ckms.service.impl;

import com.swp.ckms.dto.response.DispatchSuggestionResponse;
import com.swp.ckms.dto.response.DispatchSuggestionResponse.OrderAllocationSuggestion;
import com.swp.ckms.dto.response.DispatchSuggestionResponse.ProductDispatchSuggestion;
import com.swp.ckms.entity.*;
import com.swp.ckms.repository.*;
import com.swp.ckms.repository.projection.MaterialStockProjection;
import com.swp.ckms.service.DispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DispatchServiceImpl implements DispatchService {

    private final StoreOrderRepository storeOrderRepository;
    private final KitchenWarehouseRepository warehouseRepository;
    private final KitchenStockItemRepository kitchenStockItemRepository;
    private final RecipeRepository recipeRepository;
    private final CentralKitchenRepository kitchenRepository;

    @Override
    public DispatchSuggestionResponse suggestProductionPlan(Long kitchenId, LocalDate targetDate) {
        if (kitchenId == null || targetDate == null) {
            throw new IllegalArgumentException("Kitchen ID and Target Date must not be null");
        }
        
        // 1. Get Physical Constraints: Kitchen Max Capacity
        CentralKitchen kitchen = kitchenRepository.findById(kitchenId)
                .orElseThrow(() -> new IllegalArgumentException("Kitchen not found with ID: " + kitchenId));
        
        BigDecimal remainingKitchenCapacity = kitchen.getMaxDailyCapacity() != null ? kitchen.getMaxDailyCapacity() : BigDecimal.valueOf(999999);
        
        // Subtract already committed load for that date
        BigDecimal currentLoad = storeOrderRepository.sumQuantityByKitchenAndDeliveryDate(kitchenId, targetDate);
        if (currentLoad != null) {
            remainingKitchenCapacity = remainingKitchenCapacity.subtract(currentLoad);
        }
        if (remainingKitchenCapacity.compareTo(BigDecimal.ZERO) < 0) remainingKitchenCapacity = BigDecimal.ZERO;

        // 2. Fetch Order Pool (Pull Model: Any kitchen can see any unassigned APPROVED orders)
        List<StoreOrder> orderPool = storeOrderRepository.findAllUnassignedApprovedOrders(targetDate);
        
        // 3. Group by Product
        Map<Product, List<StoreOrder>> ordersByProduct = new HashMap<>();
        for (StoreOrder order : orderPool) {
            for (OrderDetail detail : order.getOrderDetails()) {
                Product p = detail.getProduct();
                ordersByProduct.computeIfAbsent(p, k -> new ArrayList<>()).add(order);
            }
        }

        // 4. Fetch Inventory Stock for all ingredients needed
        KitchenWarehouse warehouse = warehouseRepository.findByKitchen_KitchenId(kitchenId)
                .stream().findFirst()
                .orElse(null);
        
        List<ProductDispatchSuggestion> productSuggestions = new ArrayList<>();

        // 5. Process Each Product
        for (Map.Entry<Product, List<StoreOrder>> entry : ordersByProduct.entrySet()) {
            Product product = entry.getKey();
            List<StoreOrder> relatedOrders = entry.getValue();

            // Calculate Demand
            BigDecimal productDemand = relatedOrders.stream()
                    .flatMap(o -> o.getOrderDetails().stream())
                    .filter(d -> d.getProduct().getId().equals(product.getId()))
                    .map(d -> BigDecimal.valueOf(d.getQuantity()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Calculate Ingredient Capacity
            BigDecimal ingredientCapacity = calculateIngredientCapacity(product, warehouse);

            // Suggested Quantity
            BigDecimal suggestedQty = productDemand.min(remainingKitchenCapacity).min(ingredientCapacity);

            // Allocate suggested quantity using EDD (already sorted in query, but let's re-sort to be safe and specific)
            // Rule: deliveryDate ASC, quantity DESC, orderDate ASC
            relatedOrders.sort(Comparator.comparing(StoreOrder::getDeliveryDate)
                    .thenComparing(Comparator.comparing((StoreOrder o) -> getProductQtyInOrder(o, product.getId())).reversed())
                    .thenComparing(StoreOrder::getOrderDate));

            List<OrderAllocationSuggestion> allocationSuggestions = new ArrayList<>();
            BigDecimal remainingToAllocate = suggestedQty;

            for (StoreOrder order : relatedOrders) {
                BigDecimal requested = getProductQtyInOrder(order, product.getId());
                BigDecimal allocated;

                if (remainingToAllocate.compareTo(requested) >= 0) {
                    allocated = requested;
                    remainingToAllocate = remainingToAllocate.subtract(requested);
                } else {
                    allocated = remainingToAllocate;
                    remainingToAllocate = BigDecimal.ZERO;
                }

                allocationSuggestions.add(OrderAllocationSuggestion.builder()
                        .orderId(order.getOrderId())
                        .storeName(order.getStore().getName())
                        .requestedQty(requested)
                        .allocatedQty(allocated)
                        .isFullyAllocated(allocated.compareTo(requested) == 0 && requested.compareTo(BigDecimal.ZERO) > 0)
                        .build());
            }

            productSuggestions.add(ProductDispatchSuggestion.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .demandQty(productDemand)
                    .kitchenCapacity(remainingKitchenCapacity)
                    .ingredientCapacity(ingredientCapacity)
                    .suggestedQty(suggestedQty)
                    .allocations(allocationSuggestions)
                    .build());

            // Deduct from kitchen capacity AFTER allocating this product
            // Wait, usually multiple products share the same capacity. In some systems, it's sum of all quantities.
            // If it's a shared bucket:
            remainingKitchenCapacity = remainingKitchenCapacity.subtract(suggestedQty);
            if (remainingKitchenCapacity.compareTo(BigDecimal.ZERO) < 0) remainingKitchenCapacity = BigDecimal.ZERO;
        }

        return DispatchSuggestionResponse.builder()
                .targetDate(targetDate)
                .products(productSuggestions)
                .build();
    }

    private BigDecimal calculateIngredientCapacity(Product product, KitchenWarehouse warehouse) {
        if (warehouse == null) return BigDecimal.ZERO;

        Optional<Recipe> activeRecipe = recipeRepository.findByProductIdAndIsActiveTrue(product.getId());
        if (activeRecipe.isEmpty()) {
            log.warn("No active recipe for product ID: {}", product.getId());
            return BigDecimal.ZERO;
        }

        List<RecipeDetail> details = activeRecipe.get().getRecipeDetails();
        if (details.isEmpty()) return BigDecimal.valueOf(999999); // No ingredients needed?

        List<Long> materialIds = details.stream().map(d -> d.getMaterial().getId()).collect(Collectors.toList());
        List<MaterialStockProjection> stocks = kitchenStockItemRepository.getAvailableStockForMaterials(warehouse.getWarehouseId(), materialIds);
        
        Map<Long, BigDecimal> stockMap = stocks.stream()
                .collect(Collectors.toMap(MaterialStockProjection::getMaterialId, MaterialStockProjection::getTotalQuantity));

        BigDecimal minCapacity = BigDecimal.valueOf(999999);

        for (RecipeDetail detail : details) {
            BigDecimal stock = stockMap.getOrDefault(detail.getMaterial().getId(), BigDecimal.ZERO);
            BigDecimal neededForBatch = detail.getQuantityNeeded();
            BigDecimal yield = activeRecipe.get().getYield();
            
            if (neededForBatch.compareTo(BigDecimal.ZERO) <= 0 || yield.compareTo(BigDecimal.ZERO) <= 0) continue;

            // Capacity = (Stock / NeededForBatch) * Yield
            BigDecimal capacityForThisIngredient = stock.divide(neededForBatch, 4, RoundingMode.HALF_UP)
                    .multiply(yield)
                    .setScale(0, RoundingMode.FLOOR);
            
            if (capacityForThisIngredient.compareTo(minCapacity) < 0) {
                minCapacity = capacityForThisIngredient;
            }
        }

        return minCapacity;
    }

    private BigDecimal getProductQtyInOrder(StoreOrder order, Long productId) {
        return order.getOrderDetails().stream()
                .filter(d -> d.getProduct().getId().equals(productId))
                .map(d -> BigDecimal.valueOf(d.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
