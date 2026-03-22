package com.swp.ckms.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KitchenCreateRequest {
    @NotBlank(message = "Kitchen name is required")
    private String name;

    @NotBlank(message = "Kitchen address is required")
    private String address;

    @DecimalMin(value = "0.0", message = "Max daily capacity must be at least 0")
    private BigDecimal maxDailyCapacity;

    private Double latitude;

    private Double longitude;

    private String phone;
}
