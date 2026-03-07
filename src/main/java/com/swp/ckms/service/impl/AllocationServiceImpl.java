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
    public ProductionPlanResponse confirmAllocation(Long planId, Long requestVersion, com.swp.ckms.dto.request.AllocationAdjustmentRequest adjustmentRequest) {
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

        if (adjustmentRequest != null && adjustmentRequest.getAdjustments() != null && !adjustmentRequest.getAdjustments().isEmpty()) {
            confirmAllocationWithAdjustments(plan, adjustmentRequest);
        } else {
            allocateProducedQuantity(plan);
        }

        plan.setStatus(ProductionPlanStatus.FINISHED);
        productionPlanRepository.save(plan);

        return buildProductionPlanResponse(plan);
    }

    @Override
    @Transactional(readOnly = true)
    public com.swp.ckms.dto.response.AllocationPreviewResponse previewAllocation(Long planId) {
        if (!productionPlanRepository.existsById(planId)) {
            throw new com.swp.ckms.exception.business.ResourceNotFoundException("Production Plan not found with id: " + planId);
        }

        List<com.swp.ckms.entity.ProductionOutput> outputs = productionOutputRepository.findByProductionPlan_PlanId(planId);
        List<com.swp.ckms.entity.StoreOrder> orders = storeOrderRepository.findByProductionPlan_PlanId(planId);
        
        orders.sort((o1, o2) -> {
            java.time.LocalDateTime t1 = o1.getOrderDate();
            java.time.LocalDateTime t2 = o2.getOrderDate();
            if (t1 == null) return (t2 == null) ? 0 : -1;
            if (t2 == null) return 1;
            return t1.compareTo(t2);
        });

        Map<Long, BigDecimal> availableQtyByProduct = outputs.stream()
                .collect(Collectors.toMap(out -> out.getProduct().getId(), out -> out.getActualProducedQty()));

        List<com.swp.ckms.dto.response.AllocationPreviewResponse.ProposedOrderAllocation> proposedOrders = new java.util.ArrayList<>();

        for (com.swp.ckms.entity.StoreOrder order : orders) {
            List<com.swp.ckms.dto.response.AllocationPreviewResponse.ProposedItemAllocation> items = new java.util.ArrayList<>();
            for (com.swp.ckms.entity.OrderDetail detail : order.getOrderDetails()) {
                Long productId = detail.getProduct().getId();
                BigDecimal requestedQty = BigDecimal.valueOf(detail.getQuantity());
                BigDecimal available = availableQtyByProduct.getOrDefault(productId, BigDecimal.ZERO);

                BigDecimal proposedQty;
                if (available.compareTo(requestedQty) >= 0) {
                    proposedQty = requestedQty;
                    availableQtyByProduct.put(productId, available.subtract(requestedQty));
                } else {
                    proposedQty = available;
                    availableQtyByProduct.put(productId, BigDecimal.ZERO);
                }

                items.add(com.swp.ckms.dto.response.AllocationPreviewResponse.ProposedItemAllocation.builder()
                        .productId(productId)
                        .productName(detail.getProduct().getName())
                        .requestedQty(requestedQty)
                        .proposedQty(proposedQty)
                        .build());
            }

            proposedOrders.add(com.swp.ckms.dto.response.AllocationPreviewResponse.ProposedOrderAllocation.builder()
                    .orderId(order.getOrderId())
                    .storeName(order.getStore() != null ? order.getStore().getName() : "Unknown")
                    .items(items)
                    .build());
        }

        return com.swp.ckms.dto.response.AllocationPreviewResponse.builder()
                .planId(planId)
                .orders(proposedOrders)
                .build();
    }

    private void confirmAllocationWithAdjustments(ProductionPlan plan, com.swp.ckms.dto.request.AllocationAdjustmentRequest request) {
        List<com.swp.ckms.entity.ProductionOutput> outputs = productionOutputRepository.findByProductionPlan_PlanId(plan.getPlanId());
        Map<Long, BigDecimal> actualProducedMap = outputs.stream()
                .collect(Collectors.toMap(out -> out.getProduct().getId(), out -> out.getActualProducedQty()));

        // Validation: Σ(finalQty) <= actualProducedQty
        Map<Long, BigDecimal> totalAllocatedPerProduct = request.getAdjustments().stream()
                .collect(Collectors.groupingBy(
                        com.swp.ckms.dto.request.AllocationAdjustmentRequest.OrderItemAdjustment::getProductId,
                        Collectors.reducing(BigDecimal.ZERO, com.swp.ckms.dto.request.AllocationAdjustmentRequest.OrderItemAdjustment::getFinalQty, BigDecimal::add)
                ));

        for (Map.Entry<Long, BigDecimal> entry : totalAllocatedPerProduct.entrySet()) {
            BigDecimal actualAvailable = actualProducedMap.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            if (entry.getValue().compareTo(actualAvailable) > 0) {
                throw new com.swp.ckms.exception.business.BusinessRuleViolationException(
                    "Total allocated quantity for product " + entry.getKey() + " (" + entry.getValue() + 
                    ") exceeds actual produced quantity (" + actualAvailable + ")");
            }
        }

        // Apply adjustments
        Map<Long, List<com.swp.ckms.dto.request.AllocationAdjustmentRequest.OrderItemAdjustment>> adjustmentsByOrder = request.getAdjustments().stream()
                .collect(Collectors.groupingBy(com.swp.ckms.dto.request.AllocationAdjustmentRequest.OrderItemAdjustment::getOrderId));

        for (Map.Entry<Long, List<com.swp.ckms.dto.request.AllocationAdjustmentRequest.OrderItemAdjustment>> entry : adjustmentsByOrder.entrySet()) {
            com.swp.ckms.entity.StoreOrder order = storeOrderRepository.findById(entry.getKey())
                    .orElseThrow(() -> new com.swp.ckms.exception.business.ResourceNotFoundException("Order not found: " + entry.getKey()));
            
            BigDecimal newTotalAmount = BigDecimal.ZERO;
            
            for (com.swp.ckms.dto.request.AllocationAdjustmentRequest.OrderItemAdjustment adj : entry.getValue()) {
                com.swp.ckms.entity.OrderDetail detail = order.getOrderDetails().stream()
                        .filter(d -> d.getProduct().getId().equals(adj.getProductId()))
                        .findFirst()
                        .orElseThrow(() -> new com.swp.ckms.exception.business.ResourceNotFoundException("Product " + adj.getProductId() + " not in order " + order.getOrderId()));

                allocationItemRepository.save(com.swp.ckms.entity.AllocationItem.builder()
                        .productionPlan(plan)
                        .order(order)
                        .product(detail.getProduct())
                        .requestedQty(BigDecimal.valueOf(detail.getQuantity()))
                        .finalQty(adj.getFinalQty())
                        .build());

                if (detail.getUnitPrice() != null) {
                    newTotalAmount = newTotalAmount.add(detail.getUnitPrice().multiply(adj.getFinalQty()));
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
