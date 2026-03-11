package com.swp.ckms.enums;

public enum OrderStatus {
    DRAFT,  //Mới tạo - Chờ duyệt
    SUBMITTED,   // Bấm gửi
    APPROVED,    // Đã duyệt - Chờ vào kế hoạch
    SCHEDULED,   // Đã vào kế hoạch sản xuất
    LOCKED,      // Đang nấu - Khóa đơn hàng
    ALLOCATED,   // Đã chia hàng & giữ kho
    IN_TRANSIT,  // Đang trên xe giao hàng
    DELIVERED,   // Đã giao đến Store
    CONFIRMED,   // Đã chốt tài chính/Invoice
    REJECTED     // Đơn bị từ chối
}
