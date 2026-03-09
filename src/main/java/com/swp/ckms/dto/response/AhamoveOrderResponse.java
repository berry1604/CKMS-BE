package com.swp.ckms.dto.ahamove;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AhamoveOrderResponse {

    // ID đơn hàng AhaMove (dạng: SGNJN-ABCD-XXXX)
    @JsonProperty("order_id")
    private String orderId;

    // Link theo dõi đơn hàng public
    @JsonProperty("shared_link")
    private String sharedLink;

    // Phí vận chuyển tính được
    @JsonProperty("total_pay")
    private double totalPay;

    // Trạng thái ban đầu
    private String status;

    // Thông tin tài xế (nếu đã assign)
    @JsonProperty("supplier_id")
    private String supplierId;

    // Khoảng cách ước tính (km)
    private double distance;

    // Thời gian ước tính (giây)
    private int duration;
}