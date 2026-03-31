package com.swp.ckms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreCreateRequest {

    @NotBlank(message = "Tên cửa hàng không được để trống")
    private String name;

    private String address;

    private String paymentCycle; // e.g., "MONTHLY"
    
    private String phoneNumber;

    private Double latitude;

    private Double longitude;

}
