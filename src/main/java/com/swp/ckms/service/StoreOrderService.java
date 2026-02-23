package com.swp.ckms.service;

import com.swp.ckms.dto.request.StoreOrderRequest;
import com.swp.ckms.dto.response.StoreOrderResponse;

public interface StoreOrderService {
    StoreOrderResponse createOrder(StoreOrderRequest request, String username);
}
