package com.swp.ckms.dto.request;

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

    private List<Long> storeOrderIds;

    @NotNull(message = "Ngày kế hoạch không được để trống")
    private java.time.LocalDate plannedDate;

    private Long kitchenId;
}
