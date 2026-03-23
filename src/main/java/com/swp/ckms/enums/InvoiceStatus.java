package com.swp.ckms.enums;

public enum InvoiceStatus {
    PENDING,       // mới sinh, chưa chốt sổ
    IN_STATEMENT,  // đã được gom vào Statement
    PAID,          // đã thanh toán
    FULFILLED,     // đã giao hàng thành công, sẵn sàng tính tiền
    CANCELLED      // huỷ
}
