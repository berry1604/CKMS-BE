package com.swp.ckms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KitchenResponse {
    private Long kitchenId;
    private String name;
    private String address;
    private BigDecimal maxDailyCapacity;
    private Double latitude;
    private Double longitude;
    private String phone;
    private Long warehouseId;

    // Production status fields
    private String currentStatus;         // "IDLE" | "IN_PRODUCTION"
    private int activePlanCount;          // Số plan active hôm nay
    private BigDecimal todayUsedCapacity;  // Tổng sản lượng đã lên plan hôm nay

    private Boolean isActive;
}
