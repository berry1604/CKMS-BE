package com.swp.ckms.enums;

public enum UserStatus {
   PENDING_VERIFICATION, // user mới tạo, chưa xác minh email
    ACTIVE,               // đã xác minh, có thể login
    INACTIVE,             // bị vô hiệu hóa bởi admin
    BLOCKED,              // bị chặn (do vi phạm)
    DELETED               // đã xóa (soft delete)
}

