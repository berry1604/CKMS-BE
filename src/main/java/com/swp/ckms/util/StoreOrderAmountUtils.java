package com.swp.ckms.util;

import com.swp.ckms.entity.OrderDetail;
import com.swp.ckms.entity.StoreOrder;

import java.math.BigDecimal;
import java.util.List;

public final class StoreOrderAmountUtils {

    private StoreOrderAmountUtils() {
    }

    public static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static BigDecimal calculateOrderFeeFromDetails(List<OrderDetail> details) {
        if (details == null || details.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return details.stream()
                .map(detail -> {
                    if (detail.getUnitPrice() == null || detail.getQuantity() == null) {
                        return BigDecimal.ZERO;
                    }
                    return detail.getUnitPrice().multiply(BigDecimal.valueOf(detail.getQuantity()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static BigDecimal calculateTotalAmount(BigDecimal orderFee, BigDecimal shippingFee) {
        return zeroIfNull(orderFee).add(zeroIfNull(shippingFee));
    }

    public static void syncTotalAmount(StoreOrder order) {
        if (order == null) {
            return;
        }

        BigDecimal orderFee = zeroIfNull(order.getOrderFee());
        BigDecimal shippingFee = zeroIfNull(order.getShippingFee());

        order.setOrderFee(orderFee);
        order.setShippingFee(shippingFee);
        order.setTotalAmount(orderFee.add(shippingFee));
    }
}
