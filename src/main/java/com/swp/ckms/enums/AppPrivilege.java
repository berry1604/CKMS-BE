package com.swp.ckms.enums;

import lombok.Getter;

@Getter
public enum AppPrivilege {
    // User Management
    CREATE_USER("CREATE_USER", "Can create new users"),
    VIEW_USER("VIEW_USER", "Can view user details"),
    UPDATE_USER("UPDATE_USER", "Can update user details"),
    DELETE_USER("DELETE_USER", "Can delete users"),

    // Role Management
    CREATE_ROLE("CREATE_ROLE", "Can create new roles"),
    VIEW_ROLE("VIEW_ROLE", "Can view role details"),
    UPDATE_ROLE("UPDATE_ROLE", "Can update role details"),
    DELETE_ROLE("DELETE_ROLE", "Can delete roles"),

    // Category Management
    CREATE_CATEGORY("CREATE_CATEGORY", "Can create new categories"),
    VIEW_CATEGORY("VIEW_CATEGORY", "Can view categories"),
    UPDATE_CATEGORY("UPDATE_CATEGORY", "Can update categories"),
    DELETE_CATEGORY("DELETE_CATEGORY", "Can delete categories"),

    // Material Management
    CREATE_MATERIAL("CREATE_MATERIAL", "Can create new materials"),
    VIEW_MATERIAL("VIEW_MATERIAL", "Can view materials"),
    UPDATE_MATERIAL("UPDATE_MATERIAL", "Can update materials"),
    DELETE_MATERIAL("DELETE_MATERIAL", "Can delete materials"),
    
    // Product Management
    CREATE_PRODUCT("CREATE_PRODUCT", "Can create new products"),
    VIEW_PRODUCT("VIEW_PRODUCT", "Can view products"),
    UPDATE_PRODUCT("UPDATE_PRODUCT", "Can update products"),
    DELETE_PRODUCT("DELETE_PRODUCT", "Can delete products"),

    // Recipe Management
    CREATE_RECIPE("CREATE_RECIPE", "Can create new recipes"),
    VIEW_RECIPE("VIEW_RECIPE", "Can view recipes"),
    UPDATE_RECIPE("UPDATE_RECIPE", "Can update recipes"),
    DELETE_RECIPE("DELETE_RECIPE", "Can delete recipes"),

    // Production Plan Management
    CREATE_PRODUCTION_PLAN("CREATE_PRODUCTION_PLAN", "Can create production plans"),
    VIEW_PRODUCTION_PLAN("VIEW_PRODUCTION_PLAN", "Can view production plans"),
    UPDATE_PRODUCTION_PLAN("UPDATE_PRODUCTION_PLAN", "Can update production plans"),

    // Store Order Management
    CREATE_STORE_ORDER("CREATE_STORE_ORDER", "Can create new store orders"),
    VIEW_STORE_ORDER("VIEW_STORE_ORDER", "Can view store orders"),
    UPDATE_STORE_ORDER("UPDATE_STORE_ORDER", "Can update store orders"),

    // Shipment Management
    CREATE_SHIPMENT("CREATE_SHIPMENT", "Can create shipments"),
    VIEW_SHIPMENT("VIEW_SHIPMENT", "Can view shipments"),
    CONFIRM_SHIPMENT("CONFIRM_SHIPMENT", "Can confirm shipments"),

    // Inventory Management
    VIEW_KITCHEN_INVENTORY("VIEW_KITCHEN_INVENTORY", "Can view kitchen inventory"),
    UPDATE_KITCHEN_INVENTORY("UPDATE_KITCHEN_INVENTORY", "Can update kitchen inventory"),
    VIEW_STORE_INVENTORY("VIEW_STORE_INVENTORY", "Can view store inventory"),
    UPDATE_STORE_INVENTORY("UPDATE_STORE_INVENTORY", "Can update store inventory"),

    // Billing & Finance
    VIEW_BILLING("VIEW_BILLING", "Can view billing statements"),
    PAY_BILLING("PAY_BILLING", "Can process payments for billing"),
    VIEW_INVOICE("VIEW_INVOICE", "Can view invoices"),

    // Dashboard
    VIEW_DASHBOARD("VIEW_DASHBOARD", "Can view dashboard");

    private final String code;
    private final String description;

    AppPrivilege(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
