package com.swp.ckms.enums;

public enum ShipmentStatus {
    PENDING,        // Coordinator vừa tạo shipment
    PREPARED,       // Kitchen staff đã chuẩn bị hàng xong
    IN_TRANSIT,     // Đang vận chuyển
    DELIVERED,      // Store staff xác nhận đã nhận hàng
    CANCELLED       // Hủy shipment
}