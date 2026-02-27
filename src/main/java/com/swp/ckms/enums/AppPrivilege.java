package com.swp.ckms.enums;

import lombok.Getter;

@Getter
public enum AppPrivilege {
    // User Management
    CREATE_USER("CREATE_USER", "Có thể tạo người dùng mới"),
    VIEW_USER("VIEW_USER", "Có thể xem chi tiết người dùng"),
    UPDATE_USER("UPDATE_USER", "Có thể cập nhật thông tin người dùng"),
    DELETE_USER("DELETE_USER", "Có thể xóa người dùng"),

    // Role Management
    CREATE_ROLE("CREATE_ROLE", "Có thể tạo vai trò mới"),
    VIEW_ROLE("VIEW_ROLE", "Có thể xem chi tiết vai trò"),
    UPDATE_ROLE("UPDATE_ROLE", "Có thể cập nhật vai trò"),
    DELETE_ROLE("DELETE_ROLE", "Có thể xóa vai trò"),

    // Catalog & R&D Management (Manager)
    MANAGE_CATALOG("MANAGE_CATALOG", "Quản lý danh mục Sản phẩm, Công thức, Material, Category"),
    CREATE_CATEGORY("CREATE_CATEGORY", "Có thể tạo danh mục mới"),
    VIEW_CATEGORY("VIEW_CATEGORY", "Có thể xem danh mục"),
    UPDATE_CATEGORY("UPDATE_CATEGORY", "Có thể cập nhật danh mục"),
    DELETE_CATEGORY("DELETE_CATEGORY", "Có thể xóa danh mục"),

    CREATE_MATERIAL("CREATE_MATERIAL", "Có thể tạo nguyên liệu mới"),
    VIEW_MATERIAL("VIEW_MATERIAL", "Có thể xem nguyên liệu"),
    UPDATE_MATERIAL("UPDATE_MATERIAL", "Có thể cập nhật nguyên liệu"),
    DELETE_MATERIAL("DELETE_MATERIAL", "Có thể xóa nguyên liệu"),
    
    CREATE_PRODUCT("CREATE_PRODUCT", "Có thể tạo sản phẩm mới"),
    VIEW_PRODUCT("VIEW_PRODUCT", "Có thể xem sản phẩm"),
    UPDATE_PRODUCT("UPDATE_PRODUCT", "Có thể cập nhật sản phẩm"),
    DELETE_PRODUCT("DELETE_PRODUCT", "Có thể xóa sản phẩm"),

    CREATE_RECIPE("CREATE_RECIPE", "Có thể tạo công thức mới"),
    VIEW_RECIPE("VIEW_RECIPE", "Có thể xem công thức"),
    UPDATE_RECIPE("UPDATE_RECIPE", "Có thể cập nhật công thức"),
    DELETE_RECIPE("DELETE_RECIPE", "Có thể xóa công thức"),

    // Production Planning (Coordinator)
    ORGANIZE_PRODUCTION("ORGANIZE_PRODUCTION", "Lập kế hoạch sản xuất và điều phối"),
    CREATE_PRODUCTION_PLAN("CREATE_PRODUCTION_PLAN", "Có thể tạo kế hoạch sản xuất"),
    VIEW_PRODUCTION_PLAN("VIEW_PRODUCTION_PLAN", "Có thể xem kế hoạch sản xuất"),
    UPDATE_PRODUCTION_PLAN("UPDATE_PRODUCTION_PLAN", "Có thể cập nhật kế hoạch sản xuất"),

    // Production Execution (Kitchen Staff)
    EXECUTE_PRODUCTION("EXECUTE_PRODUCTION", "Thực thi sản xuất và cập nhật trạng thái"),

    // Store Order Management
    CREATE_STORE_ORDER("CREATE_STORE_ORDER", "Có thể tạo đơn đặt hàng mới"),
    APPROVE_STORE_ORDER("APPROVE_STORE_ORDER", "Có thể duyệt đơn đặt hàng (Coordinator)"),
    VIEW_STORE_ORDER("VIEW_STORE_ORDER", "Có thể xem đơn đặt hàng"),
    UPDATE_STORE_ORDER("UPDATE_STORE_ORDER", "Có thể cập nhật đơn đặt hàng"),

    // Shipment & Logistics
    CREATE_SHIPMENT("CREATE_SHIPMENT", "Có thể tạo shipment (Coordinator)"),
    PREPARE_SHIPMENT("PREPARE_SHIPMENT", "Có thể chuẩn bị và đóng gói shipment (Kitchen)"),
    VIEW_SHIPMENT("VIEW_SHIPMENT", "Có thể xem thông tin shipment"),
    CONFIRM_SHIPMENT("CONFIRM_SHIPMENT", "Có thể xác nhận đã nhận hàng (Store)"),

    // Inventory Management
    VIEW_KITCHEN_INVENTORY("VIEW_KITCHEN_INVENTORY", "Có thể xem tồn kho bếp"),
    UPDATE_KITCHEN_INVENTORY("UPDATE_KITCHEN_INVENTORY", "Có thể cập nhật tồn kho bếp"),
    ADJUST_KITCHEN_STOCK("ADJUST_KITCHEN_STOCK", "Điều chỉnh kho thủ công (audit/waste)"),
    VIEW_STORE_INVENTORY("VIEW_STORE_INVENTORY", "Có thể xem tồn kho cửa hàng"),
    UPDATE_STORE_INVENTORY("UPDATE_STORE_INVENTORY", "Có thể cập nhật tồn kho cửa hàng"),

    // Billing & Finance
    VIEW_BILLING("VIEW_BILLING", "Có thể xem báo cáo tài chính"),
    PAY_BILLING("PAY_BILLING", "Xử lý thanh toán billing"),
    CONFIRM_PAYMENT("CONFIRM_PAYMENT", "Xác nhận đã nhận thanh toán (Admin)"),
    VIEW_INVOICE("VIEW_INVOICE", "Có thể xem hóa đơn"),

    // Dashboard & Analysis
    VIEW_DASHBOARD("VIEW_DASHBOARD", "Có thể xem dashboard"),
    VIEW_REPORTS("VIEW_REPORTS", "Xem báo cáo phân tích hiệu suất (Manager)");

    private final String code;
    private final String description;

    AppPrivilege(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
