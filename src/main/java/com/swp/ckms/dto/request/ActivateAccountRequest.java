package com.swp.ckms.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ActivateAccountRequest {
    @NotBlank(message = "Token is required")
    private String token;

    @NotBlank(message = "Password is required")
    private String password;
}
