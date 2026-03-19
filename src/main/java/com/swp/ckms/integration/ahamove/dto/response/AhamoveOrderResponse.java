package com.swp.ckms.integration.ahamove.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Data
public class AhamoveOrderResponse {

    // ID đơn hàng AhaMove (dạng: SGNJN-ABCD-XXXX)
    @JsonProperty("order_id")
    private String orderId;

    // Link theo dõi đơn hàng public
    @JsonProperty("shared_link")
    private String sharedLink;

    // Phí vận chuyển tính được
   private String status;

    // Nested order detail
    private OrderDetail order;

    // Top-level total_pay luôn = 0, phí thực nằm trong order.total_fee
    public double getTotalPay() {
        return order != null ? order.totalFee : 0;
    }

    public double getDistance() {
        return order != null ? order.distance : 0;
    }

    public int getDuration() {
        return order != null ? order.duration : 0;
    }

    public String getSupplierId() {
        return order != null ? order.supplierId : null;
    }
        @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderDetail {
        @JsonProperty("total_fee")
        private double totalFee;

        @JsonProperty("total_pay")
        private double totalPay;

        private double distance;
        private int duration;

        @JsonProperty("supplier_id")
        private String supplierId;
    }
}