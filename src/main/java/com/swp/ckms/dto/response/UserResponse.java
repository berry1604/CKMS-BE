package com.swp.ckms.dto.response;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {

    private Long userId;
    private String username;
    private String email;
    private String fullName;
    private String role;
    private String status;
    private Boolean isActive;

}
