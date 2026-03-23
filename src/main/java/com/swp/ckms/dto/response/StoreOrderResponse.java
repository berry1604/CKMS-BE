package com.swp.ckms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreOrderResponse {
    private Long orderId;
    private Long storeId;
    private Long createdByUserId;
    private LocalDateTime orderDate;
    private String status;
    private Long batchId;
    private String batchCode;
    private BigDecimal orderFee;
    private BigDecimal shippingFee;
    private BigDecimal totalAmount;
    private java.time.LocalDate deliveryDate;
    private String storeName;
    private String storePhone;
    private List<OrderDetailResponse> orderDetails;
}
