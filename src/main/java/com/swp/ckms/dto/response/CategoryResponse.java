package com.swp.ckms.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CategoryResponse {
    private Long id;
    private String name;
    private String description;
    private Long createdByUserId;
    private String createdByUserName;
    private Boolean isActive;
}
