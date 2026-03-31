package com.swp.ckms.integration;

import com.swp.ckms.entity.*;
import com.swp.ckms.enums.*;
import com.swp.ckms.repository.*;
import com.swp.ckms.integration.ahamove.shipment.AhamoveShipmentService;
import com.swp.ckms.integration.ahamove.dto.request.AhamoveWebhookRequest;
import com.swp.ckms.integration.ahamove.shipment.impl.AhamoveShipmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShipmentWebhookFlowTest {

    @InjectMocks private AhamoveShipmentServiceImpl ahamoveShipmentService;
    @Mock private ShipmentRepository shipmentRepository;
    @Mock private StoreOrderRepository storeOrderRepository;
    @Mock private KitchenStockItemRepository kitchenStockItemRepository;
    @Mock private KitchenWarehouseRepository kitchenWarehouseRepository;
    @Mock private InventoryTransactionRepository inventoryTransactionRepository;
    @Mock private ShipmentSourcingRecordRepository shipmentSourcingRecordRepository;
    @Mock private com.swp.ckms.integration.ahamove.config.AhamoveProperties ahamoveProperties;
    @Mock private com.swp.ckms.integration.ahamove.service.AhamoveService ahamoveService;
    @Mock private com.swp.ckms.service.NotificationService notificationService;
    @Mock private com.swp.ckms.util.RecipientResolver recipientResolver;
    @Mock private com.swp.ckms.service.impl.ShipmentFeeAllocationService shipmentFeeAllocationService;

    private Shipment shipment;
    private ShipmentStop stop1, stop2;
    private Product product;
    private StoreOrder order1, order2;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId(100L);

        FranchiseStore store1 = new FranchiseStore();
        store1.setStoreId(1L);
        store1.setName("Store A");
        FranchiseStore store2 = new FranchiseStore();
        store2.setStoreId(2L);
        store2.setName("Store B");

        shipment = new Shipment();
        shipment.setShipmentId(1L);
        shipment.setAhamoveOrderId("AHA-001");
        shipment.setStatus(ShipmentStatus.IN_TRANSIT);

        stop1 = new ShipmentStop();
        stop1.setStopId(10L);
        stop1.setStopOrder(1);
        stop1.setStatus(ShipmentStopStatus.IN_TRANSIT);
        stop1.setStore(store1);

        stop2 = new ShipmentStop();
        stop2.setStopId(11L);
        stop2.setStopOrder(2);
        stop2.setStatus(ShipmentStopStatus.IN_TRANSIT);
        stop2.setStore(store2);

        shipment.setStops(Arrays.asList(stop1, stop2));

        OrderDetail d1 = new OrderDetail();
        d1.setProduct(product);
        d1.setQuantity(5);
        order1 = new StoreOrder();
        order1.setStatus(OrderStatus.IN_TRANSIT);
        order1.setOrderDetails(List.of(d1));

        OrderDetail d2 = new OrderDetail();
        d2.setProduct(product);
        d2.setQuantity(10);
        order2 = new StoreOrder();
        order2.setStatus(OrderStatus.IN_TRANSIT);
        order2.setOrderDetails(List.of(d2));

        lenient().when(shipmentRepository.findByAhamoveOrderId("AHA-001")).thenReturn(Optional.of(shipment));
        lenient().when(storeOrderRepository.findByShipmentStop_StopId(10L)).thenReturn(List.of(order1));
        lenient().when(storeOrderRepository.findByShipmentStop_StopId(11L)).thenReturn(List.of(order2));
    }

    // ===================== PARTIAL RETURN =====================

    @Test
    @DisplayName("Partial Return: Stop1=COMPLETED, Stop2=FAILED → chỉ hoàn kho Stop2")
    void partialReturn_ShouldOnlyRestoreStockForFailedStop() {
        // GIVEN: Webhook báo RETURNED với path cho biết stop1 OK, stop2 FAILED
        AhamoveWebhookRequest req = new AhamoveWebhookRequest();
        req.setOrderId("AHA-001");
        req.setStatus("COMPLETED");
        req.setSubStatus("RETURNED");

        AhamoveWebhookRequest.AhamovePathPoint pickup = new AhamoveWebhookRequest.AhamovePathPoint();
        AhamoveWebhookRequest.AhamovePathPoint p1 = new AhamoveWebhookRequest.AhamovePathPoint();
        p1.setStatus("COMPLETED");
        AhamoveWebhookRequest.AhamovePathPoint p2 = new AhamoveWebhookRequest.AhamovePathPoint();
        p2.setStatus("FAILED");
        req.setPath(List.of(pickup, p1, p2));

        // Mock sourcing records & kitchen stock
        ShipmentSourcingRecord record = new ShipmentSourcingRecord();
        record.setProduct(product);
        record.setQuantity(BigDecimal.valueOf(15));
        when(shipmentSourcingRecordRepository.findByShipment_ShipmentId(1L)).thenReturn(List.of(record));

        KitchenWarehouse kw = new KitchenWarehouse();
        kw.setWarehouseId(1L);
        when(kitchenWarehouseRepository.findById(1L)).thenReturn(Optional.of(kw));

        KitchenStockItem stock = new KitchenStockItem();
        stock.setProduct(product);
        stock.setQuantity(BigDecimal.ZERO);
        when(kitchenStockItemRepository.findByWarehouse_WarehouseIdAndProduct_Id(any(), eq(100L)))
                .thenReturn(List.of(stock));

        // WHEN
        ahamoveShipmentService.handleWebhookUpdate(req);

        // THEN
        // Shipment Status phải là ARRIVED (vì stop1 thành công) để Store có thể thấy trong danh sách "Nhận hàng"
        assertEquals(ShipmentStatus.ARRIVED, shipment.getStatus(), "Shipment status phải là ARRIVED cho kịch bản giao một phần thành công");

        // Stop1 phải là ARRIVED (đã giao thành công, chờ cửa hàng xác nhận)
        assertEquals(ShipmentStopStatus.ARRIVED, stop1.getStatus(), "Stop1 phải ARRIVED vì COMPLETED");

        // Stop2 phải là RETURNED (thất bại)
        assertEquals(ShipmentStopStatus.RETURNED, stop2.getStatus(), "Stop2 phải RETURNED vì FAILED");

        // Order2 phải được giải phóng (trả về READY, xóa link shipmentStop)
        assertNull(order2.getShipmentStop(), "Order2 phải được giải phóng khỏi stop");
        assertEquals(OrderStatus.READY, order2.getStatus(), "Order2 phải quay về READY");

        // Order1 phải chuyển sang ARRIVED (do markStopArrived trong reconcile)
        assertEquals(OrderStatus.ARRIVED, order1.getStatus(), "Order1 phải được chuyển sang ARRIVED");

        // Stock chỉ được hoàn 10 (của stop2), KHÔNG hoàn 5 (của stop1 đã giao)
        // Sourced=15, Delivered(stop1 ARRIVED)=5, Restore=15-5=10
        assertEquals(0, BigDecimal.valueOf(10).compareTo(stock.getQuantity()),
                "Chỉ hoàn kho 10 units cho stop thất bại, không hoàn 5 units của stop đã giao");

        // Verify Fee Allocation được gọi cho các đơn thành công
        verify(shipmentFeeAllocationService, times(1)).allocateForDeliveredShipment(shipment);
    }

    @Test
    @DisplayName("Status Protection: Stop đã ARRIVED không bị đè bởi trạng thái thấp hơn (IN PROCESS)")
    void statusProtection_ArrivedStop_ShouldNotBeDowngraded() {
        // GIVEN: Stop1 đã là ARRIVED
        stop1.setStatus(ShipmentStopStatus.ARRIVED);
        order1.setStatus(OrderStatus.ARRIVED);

        AhamoveWebhookRequest req = new AhamoveWebhookRequest();
        req.setOrderId("AHA-001");
        req.setStatus("IN PROCESS");
        
        AhamoveWebhookRequest.AhamovePathPoint pickup = new AhamoveWebhookRequest.AhamovePathPoint();
        AhamoveWebhookRequest.AhamovePathPoint p1 = new AhamoveWebhookRequest.AhamovePathPoint();
        p1.setStatus("IN PROCESS"); // Trạng thái thấp hơn COMPLETED
        req.setPath(List.of(pickup, p1));

        // WHEN
        ahamoveShipmentService.handleWebhookUpdate(req);

        // THEN: Vẫn phải là ARRIVED
        assertEquals(ShipmentStopStatus.ARRIVED, stop1.getStatus(), "Stop1 không được bị hạ cấp về IN_TRANSIT");
        assertEquals(OrderStatus.ARRIVED, order1.getStatus(), "Order1 không được bị hạ cấp về IN_TRANSIT");
    }

    // ===================== FULL CANCELLATION =====================

    @Test
    @DisplayName("Full Cancel: Cả 2 stop đều chưa giao → hoàn toàn bộ kho")
    void fullCancel_ShouldRestoreAllStock() {
        AhamoveWebhookRequest req = new AhamoveWebhookRequest();
        req.setOrderId("AHA-001");
        req.setStatus("CANCELLED");

        ShipmentSourcingRecord record = new ShipmentSourcingRecord();
        record.setProduct(product);
        record.setQuantity(BigDecimal.valueOf(15));
        when(shipmentSourcingRecordRepository.findByShipment_ShipmentId(1L)).thenReturn(List.of(record));

        KitchenWarehouse kw = new KitchenWarehouse();
        kw.setWarehouseId(1L);
        when(kitchenWarehouseRepository.findById(1L)).thenReturn(Optional.of(kw));

        KitchenStockItem stock = new KitchenStockItem();
        stock.setProduct(product);
        stock.setQuantity(BigDecimal.ZERO);
        when(kitchenStockItemRepository.findByWarehouse_WarehouseIdAndProduct_Id(any(), eq(100L)))
                .thenReturn(List.of(stock));

        ahamoveShipmentService.handleWebhookUpdate(req);

        // Cả 2 order đều phải được giải phóng
        assertEquals(OrderStatus.READY, order1.getStatus());
        assertEquals(OrderStatus.READY, order2.getStatus());

        // Toàn bộ 15 units phải được hoàn về kho
        assertEquals(BigDecimal.valueOf(15), stock.getQuantity(),
                "Toàn bộ 15 units phải được hoàn kho khi hủy hoàn toàn");
    }

    // ===================== STATUS MAPPING =====================

    @Test
    @DisplayName("Status Mapping: Ahamove COMPLETED → Hệ thống DELIVERED")
    void statusMapping_Completed_ShouldMapToDelivered() {
        assertEquals(ShipmentStatus.DELIVERED, ahamoveShipmentService.mapAhamoveStatus("COMPLETED"));
    }

    @Test
    @DisplayName("Status Mapping: Ahamove RETURNING → Hệ thống RETURNED")
    void statusMapping_Returning_ShouldMapToReturned() {
        assertEquals(ShipmentStatus.RETURNED, ahamoveShipmentService.mapAhamoveStatus("RETURNING"));
    }

    @Test
    @DisplayName("Status Mapping: Ahamove PICKED UP → Hệ thống IN_TRANSIT")
    void statusMapping_PickedUp_ShouldMapToInTransit() {
        assertEquals(ShipmentStatus.IN_TRANSIT, ahamoveShipmentService.mapAhamoveStatus("PICKED UP"));
    }

    @Test
    @DisplayName("Status Mapping: Ahamove CANCELLED → Hệ thống CANCELLED")
    void statusMapping_Cancelled_ShouldMapToCancelled() {
        assertEquals(ShipmentStatus.CANCELLED, ahamoveShipmentService.mapAhamoveStatus("CANCELLED"));
    }
}
