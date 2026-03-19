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
}
