package com.swp.ckms.service.impl;

import com.swp.ckms.dto.response.ProductionPlanResponse;
import com.swp.ckms.entity.ProductionPlan;
import com.swp.ckms.enums.ProductionPlanStatus;
import com.swp.ckms.exception.business.OrderAssignmentConflictException;
import com.swp.ckms.repository.*;
import com.swp.ckms.service.AllocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AllocationServiceImpl implements AllocationService {

    private final ProductionPlanRepository productionPlanRepository;
    private final ProductionOutputRepository productionOutputRepository;
    private final StoreOrderRepository storeOrderRepository;
    private final AllocationItemRepository allocationItemRepository;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ProductionPlanResponse confirmAllocation(Long planId, Long requestVersion) {
        // Step 1: Security & Identity validation
        com.swp.ckms.security.UserContext ctx = com.swp.ckms.security.SecurityUtils.getCurrentUserContext();
        if (ctx == null) {
            throw new org.springframework.security.access.AccessDeniedException("User context not found or not authenticated");
        }
        userRepository.findById(ctx.getUserId())
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("User not found in system"));
        
        ProductionPlan plan = productionPlanRepository.findById(planId)
                .orElseThrow(() -> new com.swp.ckms.exception.business.ResourceNotFoundException("Production Plan not found with id: " + planId));

        if (plan.getStatus() == ProductionPlanStatus.FINISHED) {
            return buildProductionPlanResponse(plan);
        }

        if (plan.getStatus() != ProductionPlanStatus.PRODUCED) {
            throw new OrderAssignmentConflictException("Production Plan is not in PRODUCED state. Current state: " + plan.getStatus());
        }

        if (requestVersion != null) {
            plan.setVersion(requestVersion);
        }

        allocateProducedQuantity(plan);

        plan.setStatus(ProductionPlanStatus.FINISHED);
        productionPlanRepository.save(plan);

        return buildProductionPlanResponse(plan);
    }

    private void allocateProducedQuantity(ProductionPlan plan) {
        List<com.swp.ckms.entity.ProductionOutput> outputs = productionOutputRepository.findByProductionPlan_PlanId(plan.getPlanId());
        List<com.swp.ckms.entity.StoreOrder> orders = storeOrderRepository.findByProductionPlan_PlanId(plan.getPlanId());
        
        orders.sort((o1, o2) -> {
            java.time.LocalDateTime t1 = o1.getOrderDate();
            java.time.LocalDateTime t2 = o2.getOrderDate();
            if (t1 == null) return (t2 == null) ? 0 : -1;
            if (t2 == null) return 1;
            return t1.compareTo(t2);
        });

        Map<Long, BigDecimal> availableQtyByProduct = outputs.stream()
                .collect(Collectors.toMap(out -> out.getProduct().getId(), out -> out.getActualProducedQty()));

        for (com.swp.ckms.entity.StoreOrder order : orders) {
            BigDecimal newTotalAmount = BigDecimal.ZERO;
            for (com.swp.ckms.entity.OrderDetail detail : order.getOrderDetails()) {
                Long productId = detail.getProduct().getId();
                BigDecimal requestedQty = BigDecimal.valueOf(detail.getQuantity());
                BigDecimal available = availableQtyByProduct.getOrDefault(productId, BigDecimal.ZERO);

                BigDecimal finalQty;
                if (available.compareTo(requestedQty) >= 0) {
                    finalQty = requestedQty;
                    availableQtyByProduct.put(productId, available.subtract(requestedQty));
                } else {
                    finalQty = available;
                    availableQtyByProduct.put(productId, BigDecimal.ZERO);
                }

                allocationItemRepository.save(com.swp.ckms.entity.AllocationItem.builder()
                        .productionPlan(plan)
                        .order(order)
                        .product(detail.getProduct())
                        .requestedQty(requestedQty)
                        .finalQty(finalQty)
                        .build());

                if (detail.getUnitPrice() != null) {
                    newTotalAmount = newTotalAmount.add(detail.getUnitPrice().multiply(finalQty));
                }
            }

            order.setTotalAmount(newTotalAmount);
            if (order.getInvoice() != null) {
                com.swp.ckms.entity.Invoice invoice = order.getInvoice();
                invoice.setAmount(newTotalAmount);
                invoiceRepository.save(invoice);
            }

            order.setStatus(com.swp.ckms.enums.OrderStatus.ALLOCATED);
            storeOrderRepository.save(order);
        }
    }

    private ProductionPlanResponse buildProductionPlanResponse(ProductionPlan plan) {
        return ProductionPlanResponse.builder()
                .planId(plan.getPlanId())
                .planName(plan.getPlanName())
                .batchCode(plan.getBatchCode())
                .kitchenId(plan.getKitchen().getKitchenId())
                .status(plan.getStatus().name())
                .createdAt(plan.getCreatedAt())
                .coordinatorUserId(plan.getCoordinatorUser().getUserId())
                .build();
    }
}
