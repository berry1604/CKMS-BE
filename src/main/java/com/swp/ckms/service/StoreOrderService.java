package com.swp.ckms.service;

import com.swp.ckms.dto.request.StoreOrderRequest;
import com.swp.ckms.dto.response.StoreOrderResponse;

import com.swp.ckms.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface StoreOrderService {
    StoreOrderResponse createOrder(StoreOrderRequest request, String username);
    Page<StoreOrderResponse> getMyOrders(String username, OrderStatus status, Pageable pageable);
}
