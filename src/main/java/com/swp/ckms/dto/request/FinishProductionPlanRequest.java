package com.swp.ckms.dto.request;

import jakarta.validation.Valid;
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
public class FinishProductionPlanRequest {
    @Valid
    @NotNull(message = "Outputs list cannot be null")
    @NotEmpty(message = "At least one production output must be provided")
    private List<ProductionOutputRequest> outputs;
    private Long requestVersion;
}
