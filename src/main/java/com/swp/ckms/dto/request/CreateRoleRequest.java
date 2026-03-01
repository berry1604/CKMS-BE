package com.swp.ckms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoleRequest {

    @NotBlank(message = "Role name is required")
    private String roleName;

    @NotNull(message = "Privilege IDs are required")
    private Set<Long> privilegeIds;
}
