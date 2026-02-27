package com.swp.ckms.enums;

/**
 * Phân loại biến động kho cho mục đích Audit.
 */
public enum InventoryTransactionType {
    PRODUCTION_DEDUCT, // Xuất kho sản xuất
    PRODUCTION_RETURN, // Nhập trả sản xuất (Hủy mẻ)
    PRODUCTION_ADD     // Nhập kho thành phẩm sau sản xuất
}
