package com.swp.ckms.service;

import com.swp.ckms.dto.request.StoreOrderRequest;
import com.swp.ckms.dto.response.StoreOrderResponse;

import com.swp.ckms.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface StoreOrderService {
    StoreOrderResponse createOrder(StoreOrderRequest request, String username);
    Page<StoreOrderResponse> getMyOrders(String username, OrderStatus status, Pageable pageable);
    Page<StoreOrderResponse> getAllOrders(OrderStatus status, Pageable pageable);
    StoreOrderResponse getOrderById(Long id);
    StoreOrderResponse updateOrderStatus(Long id, OrderStatus status);
    StoreOrderResponse updateOrder(Long id, StoreOrderRequest request, String username);
    void cancelOrder(Long id, String username);
    StoreOrderResponse submitOrder(Long id, String username);
    StoreOrderResponse rescheduleOrder(Long id, java.time.LocalDate newDeliveryDate);
    java.util.List<StoreOrderResponse> splitOrder(Long id, java.util.List<com.swp.ckms.dto.request.OrderItemRequest> itemsToSplit);
}
