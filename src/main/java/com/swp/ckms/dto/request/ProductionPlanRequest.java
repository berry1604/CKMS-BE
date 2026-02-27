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
public class ProductionPlanRequest {

    @NotNull(message = "Store order IDs list cannot be null")
    @NotEmpty(message = "Store order IDs list cannot be empty")
    private List<Long> storeOrderIds;
}
