package com.swp.ckms.integration.ahamove.shipment.impl;

import com.swp.ckms.entity.Shipment;
import com.swp.ckms.entity.StoreOrder;
import com.swp.ckms.enums.OrderStatus;
import com.swp.ckms.enums.ShipmentStatus;
import com.swp.ckms.integration.ahamove.dto.request.AhamoveWebhookRequest;
import com.swp.ckms.integration.ahamove.service.AhamoveService;
import com.swp.ckms.repository.ShipmentRepository;
import com.swp.ckms.repository.StoreOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AhamoveShipmentServiceImplTest {

    @Mock
    private AhamoveService ahamoveService;

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private StoreOrderRepository storeOrderRepository;

    @InjectMocks
    private AhamoveShipmentServiceImpl service;

    @Test
    void shouldMapCompletedWithInReturnAndFailedPathToDeliveryFailed() {
        Shipment shipment = Shipment.builder()
                .shipmentId(10L)
                .ahamoveOrderId("AHM-10")
                .status(ShipmentStatus.IN_TRANSIT)
                .build();

        StoreOrder order1 = StoreOrder.builder().orderId(1L).status(OrderStatus.IN_TRANSIT).build();
        StoreOrder order2 = StoreOrder.builder().orderId(2L).status(OrderStatus.IN_TRANSIT).build();
        List<StoreOrder> orders = new ArrayList<>(List.of(order1, order2));

        when(shipmentRepository.findByAhamoveOrderId("AHM-10")).thenReturn(Optional.of(shipment));
        when(storeOrderRepository.findByShipmentStop_Shipment_ShipmentId(10L)).thenReturn(orders);

        AhamoveWebhookRequest request = new AhamoveWebhookRequest();
        request.setOrderId("AHM-10");
        request.setStatus("COMPLETED");
        request.setSubStatus("IN_RETURN");
        request.setPath(List.of(point("PICKED UP"), point("FAILED"), point("FAILED")));

        boolean updated = service.handleWebhookUpdate(request);

        assertTrue(updated);
        assertEquals(ShipmentStatus.DELIVERY_FAILED, shipment.getStatus());
        assertEquals(OrderStatus.DELIVERY_FAILED, order1.getStatus());
        assertEquals(OrderStatus.DELIVERY_FAILED, order2.getStatus());
        assertNotNull(shipment.getCancelledAt());
        verify(storeOrderRepository).saveAll(orders);
        verify(shipmentRepository).save(shipment);
    }

    @Test
    void shouldMapCompletedWithReturnedSubStatusToReturned() {
        Shipment shipment = Shipment.builder()
                .shipmentId(11L)
                .ahamoveOrderId("AHM-11")
                .status(ShipmentStatus.IN_TRANSIT)
                .build();

        StoreOrder order = StoreOrder.builder().orderId(3L).status(OrderStatus.IN_TRANSIT).build();
        List<StoreOrder> orders = new ArrayList<>(List.of(order));

        when(shipmentRepository.findByAhamoveOrderId("AHM-11")).thenReturn(Optional.of(shipment));
        when(storeOrderRepository.findByShipmentStop_Shipment_ShipmentId(11L)).thenReturn(orders);

        AhamoveWebhookRequest request = new AhamoveWebhookRequest();
        request.setOrderId("AHM-11");
        request.setStatus("COMPLETED");
        request.setSubStatus("RETURNED");
        request.setPath(List.of(point("PICKED UP"), point("FAILED")));

        boolean updated = service.handleWebhookUpdate(request);

        assertTrue(updated);
        assertEquals(ShipmentStatus.RETURNED, shipment.getStatus());
        assertEquals(OrderStatus.RETURNED, order.getStatus());
        assertNotNull(shipment.getCancelledAt());
        verify(storeOrderRepository).saveAll(orders);
        verify(shipmentRepository).save(shipment);
    }

    @Test
    void shouldKeepCompletedAsDeliveredWhenAllDropoffsSucceeded() {
        Shipment shipment = Shipment.builder()
                .shipmentId(12L)
                .ahamoveOrderId("AHM-12")
                .status(ShipmentStatus.IN_TRANSIT)
                .build();

        StoreOrder order = StoreOrder.builder().orderId(4L).status(OrderStatus.IN_TRANSIT).build();
        List<StoreOrder> orders = new ArrayList<>(List.of(order));

        when(shipmentRepository.findByAhamoveOrderId("AHM-12")).thenReturn(Optional.of(shipment));
        when(storeOrderRepository.findByShipmentStop_Shipment_ShipmentId(12L)).thenReturn(orders);

        AhamoveWebhookRequest request = new AhamoveWebhookRequest();
        request.setOrderId("AHM-12");
        request.setStatus("COMPLETED");
        request.setSubStatus("COMPLETING");
        request.setPath(List.of(point("PICKED UP"), point("COMPLETED"), point("COMPLETED")));

        boolean updated = service.handleWebhookUpdate(request);

        assertTrue(updated);
        assertEquals(ShipmentStatus.DELIVERED, shipment.getStatus());
        assertEquals(OrderStatus.DELIVERED, order.getStatus());
        assertNotNull(shipment.getDeliveredAt());
        verify(storeOrderRepository).saveAll(orders);
        verify(shipmentRepository).save(shipment);
    }

    @Test
    void shouldReturnFalseWhenShipmentNotFound() {
        when(shipmentRepository.findByAhamoveOrderId("NOT_FOUND")).thenReturn(Optional.empty());

        AhamoveWebhookRequest request = new AhamoveWebhookRequest();
        request.setOrderId("NOT_FOUND");
        request.setStatus("COMPLETED");

        boolean updated = service.handleWebhookUpdate(request);

        assertFalse(updated);
        verify(shipmentRepository, never()).save(any(Shipment.class));
        verify(storeOrderRepository, never()).findByShipmentStop_Shipment_ShipmentId(anyLong());
    }

    private AhamoveWebhookRequest.AhamovePathPoint point(String status) {
        AhamoveWebhookRequest.AhamovePathPoint p = new AhamoveWebhookRequest.AhamovePathPoint();
        p.setStatus(status);
        return p;
    }
}
