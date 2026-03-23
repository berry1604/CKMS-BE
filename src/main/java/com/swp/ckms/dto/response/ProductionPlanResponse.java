package com.swp.ckms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionPlanResponse {
    private Long planId;
    private String planName;
    private String batchCode;
    private Long kitchenId;
    private String status;
    private LocalDateTime createdAt;
    private Long coordinatorUserId;
}
