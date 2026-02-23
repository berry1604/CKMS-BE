package com.swp.ckms.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreOrderRequest {
    @NotNull(message = "Store ID is required")
    private Long storeId;

    @NotEmpty(message = "Order must contain at least one item")
    private List<OrderItemRequest> items;
}
