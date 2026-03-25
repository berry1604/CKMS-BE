package com.swp.ckms.service.impl;

import com.swp.ckms.dto.request.AllocationAdjustmentRequest;
import com.swp.ckms.dto.request.AllocationAdjustmentRequest.OrderItemAdjustment;
import com.swp.ckms.dto.response.AllocationPreviewResponse;
import com.swp.ckms.dto.response.AllocationPreviewResponse.ProposedItemAllocation;
import com.swp.ckms.dto.response.AllocationPreviewResponse.ProposedOrderAllocation;
import com.swp.ckms.dto.response.ProductionPlanResponse;
import com.swp.ckms.entity.*;
import com.swp.ckms.enums.OrderStatus;
import com.swp.ckms.enums.ProductionPlanStatus;
import com.swp.ckms.exception.business.BusinessRuleViolationException;
import com.swp.ckms.exception.business.OrderAssignmentConflictException;
import com.swp.ckms.exception.business.ResourceNotFoundException;
import com.swp.ckms.repository.*;
import com.swp.ckms.security.SecurityUtils;
import com.swp.ckms.security.UserContext;
import com.swp.ckms.service.AllocationService;
import com.swp.ckms.util.StoreOrderAmountUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    public ProductionPlanResponse confirmAllocation(Long planId, Long requestVersion, AllocationAdjustmentRequest adjustmentRequest) {
        // Step 1: Security & Identity validation
        UserContext ctx = SecurityUtils.getCurrentUserContext();
        if (ctx == null) {
            throw new AccessDeniedException("User context not found or not authenticated");
        }
        userRepository.findById(ctx.getUserId())
                .orElseThrow(() -> new AccessDeniedException("User not found in system"));
        
        ProductionPlan plan = productionPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Production Plan not found with id: " + planId));

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
    public AllocationPreviewResponse previewAllocation(Long planId) {
        if (!productionPlanRepository.existsById(planId)) {
            throw new ResourceNotFoundException("Production Plan not found with id: " + planId);
        }

        List<ProductionOutput> outputs = productionOutputRepository.findByProductionPlan_PlanId(planId);
        List<StoreOrder> orders = storeOrderRepository.findByProductionPlan_PlanId(planId);
        
        orders.sort((o1, o2) -> {
            LocalDateTime t1 = o1.getOrderDate();
            LocalDateTime t2 = o2.getOrderDate();
            if (t1 == null) return (t2 == null) ? 0 : -1;
            if (t2 == null) return 1;
            return t1.compareTo(t2);
        });

        Map<Long, BigDecimal> availableQtyByProduct = outputs.stream()
                .collect(Collectors.toMap(out -> out.getProduct().getId(), out -> out.getActualProducedQty()));

        List<ProposedOrderAllocation> proposedOrders = new ArrayList<>();

        for (StoreOrder order : orders) {
            List<ProposedItemAllocation> items = new ArrayList<>();
            for (OrderDetail detail : order.getOrderDetails()) {
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

                items.add(ProposedItemAllocation.builder()
                        .productId(productId)
                        .productName(detail.getProduct().getName())
                        .requestedQty(requestedQty)
                        .proposedQty(proposedQty)
                        .build());
            }

            proposedOrders.add(ProposedOrderAllocation.builder()
                    .orderId(order.getOrderId())
                    .storeName(order.getStore() != null ? order.getStore().getName() : "Unknown")
                    .items(items)
                    .build());
        }

        return AllocationPreviewResponse.builder()
                .planId(planId)
                .orders(proposedOrders)
                .build();
    }

    private void confirmAllocationWithAdjustments(ProductionPlan plan, AllocationAdjustmentRequest request) {
        List<ProductionOutput> outputs = productionOutputRepository.findByProductionPlan_PlanId(plan.getPlanId());
        Map<Long, BigDecimal> actualProducedMap = outputs.stream()
                .collect(Collectors.toMap(out -> out.getProduct().getId(), out -> out.getActualProducedQty()));

        // Validation: Σ(finalQty) <= actualProducedQty
        Map<Long, BigDecimal> totalAllocatedPerProduct = request.getAdjustments().stream()
                .collect(Collectors.groupingBy(
                        OrderItemAdjustment::getProductId,
                        Collectors.reducing(BigDecimal.ZERO, OrderItemAdjustment::getFinalQty, BigDecimal::add)
                ));

        for (Map.Entry<Long, BigDecimal> entry : totalAllocatedPerProduct.entrySet()) {
            BigDecimal actualAvailable = actualProducedMap.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            if (entry.getValue().compareTo(actualAvailable) > 0) {
                throw new BusinessRuleViolationException(
                    "Total allocated quantity for product " + entry.getKey() + " (" + entry.getValue() + 
                    ") exceeds actual produced quantity (" + actualAvailable + ")");
            }
        }

        // Apply adjustments
        Map<Long, List<OrderItemAdjustment>> adjustmentsByOrder = request.getAdjustments().stream()
                .collect(Collectors.groupingBy(OrderItemAdjustment::getOrderId));

        for (Map.Entry<Long, List<OrderItemAdjustment>> entry : adjustmentsByOrder.entrySet()) {
            StoreOrder order = storeOrderRepository.findById(entry.getKey())
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + entry.getKey()));
            
            BigDecimal newOrderFee = BigDecimal.ZERO;
            
            for (OrderItemAdjustment adj : entry.getValue()) {
                OrderDetail detail = order.getOrderDetails().stream()
                        .filter(d -> d.getProduct().getId().equals(adj.getProductId()))
                        .findFirst()
                        .orElseThrow(() -> new ResourceNotFoundException("Product " + adj.getProductId() + " not in order " + order.getOrderId()));

                allocationItemRepository.save(AllocationItem.builder()
                        .productionPlan(plan)
                        .order(order)
                        .product(detail.getProduct())
                        .requestedQty(BigDecimal.valueOf(detail.getQuantity()))
                        .finalQty(adj.getFinalQty())
                        .build());

                if (detail.getUnitPrice() != null) {
                    newOrderFee = newOrderFee.add(detail.getUnitPrice().multiply(adj.getFinalQty()));
                }
            }

            order.setOrderFee(newOrderFee);
            StoreOrderAmountUtils.syncTotalAmount(order);
            if (order.getInvoice() != null) {
                Invoice invoice = order.getInvoice();
                invoice.setAmount(order.getTotalAmount());
                invoiceRepository.save(invoice);
            }

            order.setStatus(OrderStatus.READY);
            storeOrderRepository.save(order);
        }
    }

    private void allocateProducedQuantity(ProductionPlan plan) {
        List<ProductionOutput> outputs = productionOutputRepository.findByProductionPlan_PlanId(plan.getPlanId());
        List<StoreOrder> orders = storeOrderRepository.findByProductionPlan_PlanId(plan.getPlanId());
        
        orders.sort((o1, o2) -> {
            LocalDateTime t1 = o1.getOrderDate();
            LocalDateTime t2 = o2.getOrderDate();
            if (t1 == null) return (t2 == null) ? 0 : -1;
            if (t2 == null) return 1;
            return t1.compareTo(t2);
        });

        Map<Long, BigDecimal> availableQtyByProduct = outputs.stream()
                .collect(Collectors.toMap(out -> out.getProduct().getId(), out -> out.getActualProducedQty()));

        for (StoreOrder order : orders) {
            BigDecimal newOrderFee = BigDecimal.ZERO;
            for (OrderDetail detail : order.getOrderDetails()) {
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

                allocationItemRepository.save(AllocationItem.builder()
                        .productionPlan(plan)
                        .order(order)
                        .product(detail.getProduct())
                        .requestedQty(requestedQty)
                        .finalQty(finalQty)
                        .build());

                if (detail.getUnitPrice() != null) {
                    newOrderFee = newOrderFee.add(detail.getUnitPrice().multiply(finalQty));
                }
            }

            order.setOrderFee(newOrderFee);
            StoreOrderAmountUtils.syncTotalAmount(order);
            if (order.getInvoice() != null) {
                Invoice invoice = order.getInvoice();
                invoice.setAmount(order.getTotalAmount());
                invoiceRepository.save(invoice);
            }

            order.setStatus(OrderStatus.READY);
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
