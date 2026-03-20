package com.swp.ckms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreUpdateRequest {

    @NotBlank(message = "Tên cửa hàng không được để trống")
    private String name;

    private String address;

    private String paymentCycle;
    
    private String phoneNumber;

}