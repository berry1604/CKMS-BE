package com.swp.ckms.dto.response;

import com.swp.ckms.enums.UnitType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MaterialResponse {
    private Long id;
    private String name;
    private UnitType unit;
    private Boolean isActive;
}
