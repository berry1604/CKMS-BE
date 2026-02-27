package com.swp.ckms.repository.projection;

import com.swp.ckms.entity.Material;
import java.math.BigDecimal;

public interface MaterialRequirementProjection {
    Material getMaterial();
    BigDecimal getTotal();
}
