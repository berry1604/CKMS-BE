package com.swp.ckms.service;

import com.swp.ckms.dto.ahamove.AhamoveOrderRequest;
import com.swp.ckms.dto.ahamove.AhamoveOrderResponse;

public interface AhamoveService {
    /**
     * Xác thực với AhaMove bằng API key, trả về access token.
     * Token này cần được dùng trong tất cả các request tiếp theo.
     */
    String getAccessToken();

    /**
     * Tạo đơn giao hàng mới trên hệ thống AhaMove.
     * Nhận vào token xác thực và thông tin đơn hàng.
     * Trả về thông tin đơn đã tạo gồm orderId và tracking link.
     */
    AhamoveOrderResponse createOrder(String token, AhamoveOrderRequest request);


    /**
     * Hủy đơn hàng đang tồn tại trên AhaMove.
     * Gọi khi coordinator cancel shipment sau khi đã dispatch.
     */
    void cancelOrder(String token, String ahamoveOrderId, String comment);

    /**
     * Lấy thông tin chi tiết đơn hàng từ AhaMove.
     * Dùng để polling trạng thái khi webhook không đến.
     */
    AhamoveOrderResponse getOrderDetail(String token, String ahamoveOrderId);
}