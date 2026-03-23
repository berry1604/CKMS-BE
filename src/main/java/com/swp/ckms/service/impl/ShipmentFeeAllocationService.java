package com.swp.ckms.service.impl;

import com.swp.ckms.entity.Invoice;
import com.swp.ckms.entity.Shipment;
import com.swp.ckms.entity.StoreOrder;
import com.swp.ckms.repository.InvoiceRepository;
import com.swp.ckms.repository.StoreOrderRepository;
import com.swp.ckms.util.StoreOrderAmountUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShipmentFeeAllocationService {

    private final StoreOrderRepository storeOrderRepository;
    private final InvoiceRepository invoiceRepository;

    @Transactional
    public void allocateForDeliveredShipment(Shipment shipment) {
        if (shipment == null || shipment.getShipmentId() == null) {
            return;
        }

        List<StoreOrder> shipmentOrders = storeOrderRepository.findByShipmentStop_Shipment_ShipmentId(shipment.getShipmentId());
        if (shipmentOrders.isEmpty()) {
            return;
        }

        BigDecimal shipmentFee = StoreOrderAmountUtils.zeroIfNull(shipment.getShippingFee());
        if (shipmentFee.compareTo(BigDecimal.ZERO) <= 0) {
            setShippingFeeAndSync(shipmentOrders, BigDecimal.ZERO);
            return;
        }

        BigDecimal totalOrderFee = shipmentOrders.stream()
                .map(order -> StoreOrderAmountUtils.zeroIfNull(order.getOrderFee()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalOrderFee.compareTo(BigDecimal.ZERO) <= 0) {
            setShippingFeeAndSync(shipmentOrders, BigDecimal.ZERO);
            return;
        }

        List<ShareAllocation> allocations = buildShareAllocations(shipmentOrders, shipmentFee, totalOrderFee);
        allocations.forEach(share -> {
            StoreOrder order = share.order();
            order.setShippingFee(centsToMoney(share.cents()));
            StoreOrderAmountUtils.syncTotalAmount(order);
        });

        persistOrdersAndInvoices(shipmentOrders);
        log.info("Allocated shipping fee for shipment #{} across {} order(s)", shipment.getShipmentId(), shipmentOrders.size());
    }

    private void setShippingFeeAndSync(List<StoreOrder> orders, BigDecimal shippingFee) {
        for (StoreOrder order : orders) {
            order.setShippingFee(shippingFee);
            StoreOrderAmountUtils.syncTotalAmount(order);
        }
        persistOrdersAndInvoices(orders);
    }

    private List<ShareAllocation> buildShareAllocations(List<StoreOrder> orders, BigDecimal shipmentFee, BigDecimal totalOrderFee) {
        long shipmentCents = toCents(shipmentFee);
        List<ShareAllocation> allocations = new ArrayList<>();
        long distributedBaseCents = 0L;

        for (StoreOrder order : orders) {
            BigDecimal orderFee = StoreOrderAmountUtils.zeroIfNull(order.getOrderFee());
            if (orderFee.compareTo(BigDecimal.ZERO) <= 0) {
                allocations.add(new ShareAllocation(order, 0L, BigDecimal.ZERO));
                continue;
            }

            BigDecimal exactCents = BigDecimal.valueOf(shipmentCents)
                    .multiply(orderFee)
                    .divide(totalOrderFee, 10, RoundingMode.HALF_UP);

            long baseCents = exactCents.setScale(0, RoundingMode.DOWN).longValue();
            BigDecimal remainder = exactCents.subtract(BigDecimal.valueOf(baseCents));

            allocations.add(new ShareAllocation(order, baseCents, remainder));
            distributedBaseCents += baseCents;
        }

        long remainingCents = shipmentCents - distributedBaseCents;
        if (remainingCents > 0) {
            allocations.sort(Comparator
                    .comparing(ShareAllocation::remainder, Comparator.reverseOrder())
                    .thenComparing(allocation -> allocation.order().getOrderId()));

            for (int i = 0; i < allocations.size() && remainingCents > 0; i++) {
                ShareAllocation current = allocations.get(i);
                if (StoreOrderAmountUtils.zeroIfNull(current.order().getOrderFee()).compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                allocations.set(i, new ShareAllocation(current.order(), current.cents() + 1L, current.remainder()));
                remainingCents--;
                if (i == allocations.size() - 1 && remainingCents > 0) {
                    i = -1;
                }
            }
        }

        return allocations;
    }

    private void persistOrdersAndInvoices(List<StoreOrder> orders) {
        storeOrderRepository.saveAll(orders);

        List<Invoice> invoicesToUpdate = orders.stream()
                .map(StoreOrder::getInvoice)
                .filter(invoice -> invoice != null)
                .toList();

        for (Invoice invoice : invoicesToUpdate) {
            StoreOrder order = invoice.getOrder();
            if (order != null) {
                invoice.setAmount(StoreOrderAmountUtils.calculateTotalAmount(order.getOrderFee(), order.getShippingFee()));
            }
        }

        if (!invoicesToUpdate.isEmpty()) {
            invoiceRepository.saveAll(invoicesToUpdate);
        }
    }

    private long toCents(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValue();
    }

    private BigDecimal centsToMoney(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }

    private record ShareAllocation(StoreOrder order, long cents, BigDecimal remainder) {
    }
}
