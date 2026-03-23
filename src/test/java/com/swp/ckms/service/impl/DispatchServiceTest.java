package com.swp.ckms.service.impl;

import com.swp.ckms.dto.response.DispatchSuggestionResponse;
import com.swp.ckms.entity.CentralKitchen;
import com.swp.ckms.entity.FranchiseStore;
import com.swp.ckms.entity.KitchenWarehouse;
import com.swp.ckms.entity.Material;
import com.swp.ckms.entity.OrderDetail;
import com.swp.ckms.entity.Product;
import com.swp.ckms.entity.Recipe;
import com.swp.ckms.entity.RecipeDetail;
import com.swp.ckms.entity.StoreOrder;
import com.swp.ckms.repository.CentralKitchenRepository;
import com.swp.ckms.repository.KitchenStockItemRepository;
import com.swp.ckms.repository.KitchenWarehouseRepository;
import com.swp.ckms.repository.RecipeRepository;
import com.swp.ckms.repository.StoreOrderRepository;
import com.swp.ckms.repository.projection.MaterialStockProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DispatchServiceTest {

    @Mock
    private StoreOrderRepository storeOrderRepository;
    @Mock
    private KitchenWarehouseRepository warehouseRepository;
    @Mock
    private KitchenStockItemRepository kitchenStockItemRepository;
    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private CentralKitchenRepository kitchenRepository;

    @InjectMocks
    private DispatchServiceImpl dispatchService;

    private CentralKitchen kitchen;
    private Product product;
    private Recipe recipe;
    private Material material;
    private KitchenWarehouse warehouse;

    @BeforeEach
    void setUp() {
        kitchen = CentralKitchen.builder()
                .kitchenId(1L)
                .maxDailyCapacity(BigDecimal.valueOf(100))
                .build();

        product = Product.builder().id(1L).name("Test Product").build();
        material = Material.builder().id(1L).name("Test Material").build();

        RecipeDetail detail = RecipeDetail.builder()
                .material(material)
                .quantityNeeded(BigDecimal.valueOf(2))
                .build();

        recipe = Recipe.builder()
                .recipeId(1L)
                .product(product)
                .isActive(true)
                .recipeDetails(Collections.singletonList(detail))
                .build();

        warehouse = KitchenWarehouse.builder().warehouseId(1L).build();
    }

    @Test
    void testSuggestPlan_ShouldPrioritizeByEDD() {
        LocalDate targetDate = LocalDate.now();
        
        // Setup Kitchen & Warehouse
        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(kitchen));
        when(warehouseRepository.findByKitchen_KitchenId(1L)).thenReturn(Collections.singletonList(warehouse));
        when(storeOrderRepository.sumQuantityByKitchenAndDeliveryDate(anyLong(), any())).thenReturn(BigDecimal.ZERO);

        // Setup Recipe & Stock (Enough for 100 units)
        when(recipeRepository.findByProductIdAndIsActiveTrue(1L)).thenReturn(Optional.of(recipe));
        MaterialStockProjection stockProjection = new MaterialStockProjection() {
            @Override public Long getMaterialId() { return 1L; }
            @Override public BigDecimal getTotalQuantity() { return BigDecimal.valueOf(200); }
        };
        when(kitchenStockItemRepository.getAvailableStockForMaterials(eq(1L), anyList()))
                .thenReturn(Collections.singletonList(stockProjection));

        // Setup Orders (EDD rule: order1 is earlier than order2)
        StoreOrder order1 = createOrder(101L, targetDate.plusDays(1), 10);
        StoreOrder order2 = createOrder(102L, targetDate.plusDays(2), 10);
        
        // Return unordered list, service should sort them
        when(storeOrderRepository.findUnassignedApprovedOrders(eq(1L), eq(targetDate)))
                .thenReturn(Arrays.asList(order2, order1));

        // Execute
        DispatchSuggestionResponse response = dispatchService.suggestProductionPlan(1L, targetDate);

        // Verify
        assertNotNull(response);
        assertEquals(1, response.getProducts().size());
        
        List<DispatchSuggestionResponse.OrderAllocationSuggestion> allocations = response.getProducts().get(0).getAllocations();
        assertEquals(2, allocations.size());
        
        // Assert EDD order
        assertEquals(101L, allocations.get(0).getOrderId());
        assertEquals(102L, allocations.get(1).getOrderId());
    }

    @Test
    void testSuggestPlan_ShouldRespectKitchenCapacity() {
        LocalDate targetDate = LocalDate.now();
        kitchen.setMaxDailyCapacity(BigDecimal.valueOf(15)); // Only 15 units capacity

        when(kitchenRepository.findById(1L)).thenReturn(Optional.of(kitchen));
        when(warehouseRepository.findByKitchen_KitchenId(1L)).thenReturn(Collections.singletonList(warehouse));
        when(storeOrderRepository.sumQuantityByKitchenAndDeliveryDate(anyLong(), any())).thenReturn(BigDecimal.ZERO);

        // Enough stock
        when(recipeRepository.findByProductIdAndIsActiveTrue(1L)).thenReturn(Optional.of(recipe));
        MaterialStockProjection stockProjection = new MaterialStockProjection() {
            @Override public Long getMaterialId() { return 1L; }
            @Override public BigDecimal getTotalQuantity() { return BigDecimal.valueOf(1000); }
        };
        when(kitchenStockItemRepository.getAvailableStockForMaterials(anyLong(), anyList())).thenReturn(Collections.singletonList(stockProjection));

        // Demand = 20 (10 + 10)
        StoreOrder order1 = createOrder(101L, targetDate.plusDays(1), 10);
        StoreOrder order2 = createOrder(102L, targetDate.plusDays(1), 10);
        when(storeOrderRepository.findUnassignedApprovedOrders(anyLong(), any())).thenReturn(Arrays.asList(order1, order2));

        // Execute
        DispatchSuggestionResponse response = dispatchService.suggestProductionPlan(1L, targetDate);

        // Verify
        assertEquals(BigDecimal.valueOf(15), response.getProducts().get(0).getSuggestedQty());
        
        List<DispatchSuggestionResponse.OrderAllocationSuggestion> allocations = response.getProducts().get(0).getAllocations();
        assertEquals(10, allocations.get(0).getAllocatedQty().intValue()); // First order gets full 10
        assertEquals(5, allocations.get(1).getAllocatedQty().intValue());  // Second order only gets remaining 5
    }

    private StoreOrder createOrder(Long id, LocalDate deliveryDate, Integer qty) {
        FranchiseStore store = FranchiseStore.builder().name("Store " + id).build();
        StoreOrder order = StoreOrder.builder()
                .orderId(id)
                .deliveryDate(deliveryDate)
                .orderDate(java.time.LocalDateTime.now())
                .store(store)
                .build();
        
        OrderDetail detail = OrderDetail.builder()
                .product(product)
                .quantity(qty)
                .order(order)
                .build();
        order.setOrderDetails(Collections.singletonList(detail));
        return order;
    }
}
