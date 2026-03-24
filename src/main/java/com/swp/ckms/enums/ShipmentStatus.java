package com.swp.ckms.enums;

public enum ShipmentStatus {
    PENDING,        // Coordinator vừa tạo shipment
    PREPARED,       // Kitchen staff đã chuẩn bị hàng xong
    IN_TRANSIT,     // Đang vận chuyển
    ARRIVED,        // Tài xế báo đã đến nơi (chờ store confirm)
    DELIVERED,      // Store staff xác nhận đã nhận hàng
    DELIVERY_FAILED,// Giao hàng thất bại
    RETURNED,       // Hàng đã hoàn trả về điểm gửi
    CANCELLED       // Hủy shipment
}