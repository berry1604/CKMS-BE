package com.swp.ckms.enums;

public enum InvoiceStatus {
    PENDING,       // mới sinh, chưa chốt sổ
    IN_STATEMENT,  // đã được gom vào Statement
    PAID,          // đã thanh toán
    CANCELLED      // huỷ
}
