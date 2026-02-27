package com.swp.ckms.repository.projection;

import java.math.BigDecimal;

public interface MaterialStockProjection {
    Long getMaterialId();
    BigDecimal getTotalQuantity();
}
