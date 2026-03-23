package com.swp.ckms.dto.request;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    private String fullName;
    private Long roleId;
    private String status;
    private Boolean isActive;
}
