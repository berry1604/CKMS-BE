package com.swp.ckms.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePrivilegeRequest {

    @NotBlank(message = "Privilege code is required")
    private String code;

    private String description;
}
