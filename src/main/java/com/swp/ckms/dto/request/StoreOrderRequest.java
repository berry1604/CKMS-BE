package com.swp.ckms.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import jakarta.validation.constraints.FutureOrPresent;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreOrderRequest {

    @NotEmpty(message = "Order must contain at least one item")
    private List<OrderItemRequest> items;

    @NotNull(message = "Delivery date is required")
    @FutureOrPresent(message = "Ngày giao hàng phải là hôm nay hoặc trong tương lai")
    private java.time.LocalDate deliveryDate;
}
