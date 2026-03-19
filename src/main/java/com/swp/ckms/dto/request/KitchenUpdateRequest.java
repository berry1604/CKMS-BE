package com.swp.ckms.dto.request;

import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KitchenUpdateRequest {
    private String name;
    private String address;

    @DecimalMin(value = "0.0", message = "Max daily capacity must be at least 0")
    private BigDecimal maxDailyCapacity;
}
