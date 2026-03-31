package com.swp.ckms.integration;

import com.swp.ckms.entity.*;
import com.swp.ckms.enums.*;
import com.swp.ckms.repository.*;
import com.swp.ckms.service.ShipmentService;
import com.swp.ckms.integration.ahamove.shipment.AhamoveShipmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class ShipmentIntegrationTest {

    @Autowired
    private ShipmentService shipmentService;

    @Autowired
    private KitchenStockItemRepository kitchenStockItemRepository;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @MockitoBean
    private AhamoveShipmentService ahamoveShipmentService;

    @Autowired private CentralKitchenRepository centralKitchenRepository;
    @Autowired private KitchenWarehouseRepository kitchenWarehouseRepository;
    @Autowired private FranchiseStoreRepository franchiseStoreRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private StoreOrderRepository storeOrderRepository;
    @Autowired private ShipmentStopRepository shipmentStopRepository;
    @Autowired private ShipmentSourcingRecordRepository shipmentSourcingRecordRepository;
    @Autowired private CategoryRepository categoryRepository;

    private Long testShipmentId;
    private Long testProductId;

    private void setupFullShipmentGraph() {
        CentralKitchen kitchen = centralKitchenRepository.save(CentralKitchen.builder().name("Test Kitchen").address("K-Addr").build());
        KitchenWarehouse kwh = kitchenWarehouseRepository.save(KitchenWarehouse.builder().warehouseId(1L).kitchen(kitchen).name("Main KW").build());
        FranchiseStore store = franchiseStoreRepository.save(FranchiseStore.builder().name("Test Store").address("S-Addr").build());
        Product prod = productRepository.save(Product.builder().name("Test Product").price(java.math.BigDecimal.valueOf(100)).unit(com.swp.ckms.enums.UnitType.PIECE).category(categoryRepository.save(com.swp.ckms.entity.Category.builder().name("Test Cat").build())).build());
        testProductId = prod.getId();

        kitchenStockItemRepository.save(KitchenStockItem.builder().warehouse(kwh).product(prod).quantity(java.math.BigDecimal.valueOf(100)).build());

        Shipment shipment = shipmentRepository.save(Shipment.builder().status(ShipmentStatus.PREPARED).build());
        testShipmentId = shipment.getShipmentId();

        ShipmentStop stop = shipmentStopRepository.save(ShipmentStop.builder().shipment(shipment).store(store).status(ShipmentStopStatus.PENDING).stopOrder(1).build());
        
        StoreOrder order = storeOrderRepository.save(StoreOrder.builder().shipmentStop(stop).store(store).status(OrderStatus.READY).build());
        
        shipmentSourcingRecordRepository.save(ShipmentSourcingRecord.builder()
                .shipment(shipment).product(prod).quantity(java.math.BigDecimal.valueOf(10)).build());

        shipment.setStops(java.util.List.of(stop));
        stop.setStoreOrders(java.util.List.of(order));
    }

    @Test
    @Transactional
    void testStartTransit_ShouldRollbackStock_WhenAhamoveFails() {
        setupFullShipmentGraph();

        // 1. Initial State
        KitchenStockItem stockItem = kitchenStockItemRepository.findAll().stream()
                .filter(s -> s.getProduct() != null && s.getProduct().getId().equals(testProductId))
                .findFirst().orElseThrow();
        BigDecimal initialQty = stockItem.getQuantity();

        // 2. Mock Ahamove to FAIL
        doThrow(new RuntimeException("AhaMove API Down")).when(ahamoveShipmentService).dispatchToAhamove(any());

        // 3. Execute
        // Note: If the try-catch is present in Service, this won't throw exception, but we check stock
        try {
            shipmentService.startTransit(testShipmentId);
        } catch (Exception e) {
            // expected if no try-catch
        }

        // 4. Verify Rollback: Stock should remain the same (initialQty)
        KitchenStockItem stockAfter = kitchenStockItemRepository.findById(stockItem.getId()).orElseThrow();
        assertEquals(initialQty.stripTrailingZeros(), stockAfter.getQuantity().stripTrailingZeros(), 
                "Stock MUST not be deducted if Ahamove call fails");
    }

}
