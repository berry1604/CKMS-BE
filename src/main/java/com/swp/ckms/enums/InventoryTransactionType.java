package com.swp.ckms.enums;

/**
 * Phân loại biến động kho cho mục đích Audit.
 */
public enum InventoryTransactionType {
    PRODUCTION_DEDUCT, // Xuất kho sản xuất
    PRODUCTION_RETURN, // Nhập trả sản xuất (Hủy mẻ)
    PRODUCTION_ADD,    // Nhập kho thành phẩm sau sản xuất
    SHIPMENT_OUT,      // Xuất kho đi giao hàng (Bếp -> Store)
    SHIPMENT_IN,       // Nhập kho từ đơn giao hàng (Store nhận)
    STORE_ADJUST,      // Điều chỉnh kho Store (Hao hụt, kiểm kê)
    KITCHEN_ADJUST     // Điều chỉnh kho Bếp
}
