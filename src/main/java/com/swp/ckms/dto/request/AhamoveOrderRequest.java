package com.swp.ckms.dto.ahamove;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AhamoveOrderRequest {

    // Loại dịch vụ: SGN-BIKE, SGN-EXPRESS, HAN-BIKE...
    @JsonProperty("service_id")
    private String serviceId;

    // Danh sách điểm: [0] = pickup (bếp), [1] = dropoff (cửa hàng)
    private List<AhamovePoint> path;

    // Danh sách hàng hóa
    private List<AhamoveItem> items;

    // Thông tin thanh toán
    @JsonProperty("payment_method")
    private String paymentMethod; // "BALANCE"

    // Ghi chú
    private String remarks;

    @Data
    @Builder
    public static class AhamovePoint {
        // Địa chỉ điểm giao/nhận
        private String address;

        // Tọa độ
        private double lat;
        private double lng;

        // Tên người nhận/gửi tại điểm này
        private String name;

        // SĐT người nhận/gửi
        private String mobile;

        // Ghi chú tại điểm này
        private String remarks;
    }

    @Data
    @Builder
    public static class AhamoveItem {
        // Mã sản phẩm (nội bộ)
        private String id;

        // Tên sản phẩm
        private String name;

        // Số lượng
        private int num;

        // Giá trị (nếu cần bảo hiểm)
        private double price;
    }
}