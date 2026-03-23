package com.swp.ckms.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateUserResponse {
    private String username;
    private String email;
    private String fullName;
    private String token;
    private String message;
}
