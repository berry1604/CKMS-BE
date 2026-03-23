package com.swp.ckms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionPlanSummaryResponse {
    private Long planId;
    private String planName;
    private String batchCode;
    private Long kitchenId;
    private String status;
    private LocalDateTime createdAt;
    private Long coordinatorUserId;
}
