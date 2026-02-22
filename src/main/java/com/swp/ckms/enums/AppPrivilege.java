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
    
    // Other example privileges
    VIEW_DASHBOARD("VIEW_DASHBOARD", "Can view dashboard");

    private final String code;
    private final String description;

    AppPrivilege(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
