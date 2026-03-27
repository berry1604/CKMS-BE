package com.swp.ckms.service.impl;

import com.swp.ckms.dto.request.OrderItemRequest;
import com.swp.ckms.dto.request.StoreOrderRequest;
import com.swp.ckms.dto.response.OrderDetailResponse;
import com.swp.ckms.dto.response.StoreOrderResponse;
import com.swp.ckms.entity.CentralKitchen;
import com.swp.ckms.entity.FranchiseStore;
import com.swp.ckms.entity.OrderDetail;
import com.swp.ckms.entity.Product;
import com.swp.ckms.entity.StoreOrder;
import com.swp.ckms.entity.User;
import com.swp.ckms.entity.AllocationItem;
import com.swp.ckms.enums.InvoiceStatus;
import com.swp.ckms.enums.OrderStatus;
import com.swp.ckms.exception.business.ResourceNotFoundException;
import com.swp.ckms.repository.AllocationItemRepository;
import com.swp.ckms.repository.FranchiseStoreRepository;
import com.swp.ckms.repository.InvoiceRepository;
import com.swp.ckms.repository.ProductRepository;
import com.swp.ckms.repository.StoreOrderRepository;
import com.swp.ckms.repository.StoreStockItemRepository;
import com.swp.ckms.repository.StoreWarehouseRepository;
import com.swp.ckms.repository.UserRepository;
import com.swp.ckms.repository.CentralKitchenRepository;
import com.swp.ckms.repository.specification.StoreOrderSpecification;
import com.swp.ckms.entity.StoreWarehouse;
import com.swp.ckms.exception.business.BusinessRuleViolationException;
import com.swp.ckms.security.SecurityUtils;
import com.swp.ckms.security.UserContext;
import com.swp.ckms.service.StoreOrderService;
import com.swp.ckms.util.StoreOrderAmountUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class StoreOrderServiceImpl implements StoreOrderService {

    private final StoreOrderRepository storeOrderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final FranchiseStoreRepository franchiseStoreRepository;
    private final InvoiceRepository invoiceRepository;
    private final AllocationItemRepository allocationItemRepository;
    private final com.swp.ckms.service.NotificationService notificationService;
    private final com.swp.ckms.util.RecipientResolver recipientResolver;
    private final CentralKitchenRepository centralKitchenRepository;

    @Override
    public StoreOrderResponse createOrder(StoreOrderRequest request, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));

        FranchiseStore store = user.getStore();
        if (store == null) {
            throw new IllegalArgumentException("User does not belong to any store");
        }

        StoreOrder order = StoreOrder.builder()
                .store(store)
                .createdByUser(user)
                .orderDate(LocalDateTime.now())
                .deliveryDate(request.getDeliveryDate())
                .status(OrderStatus.DRAFT)
                .batchId(null)
                .build();


        List<Long> productIds = request.getItems().stream()
                .map(OrderItemRequest::getProductId)
                .collect(Collectors.toList());

        java.util.Map<Long, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<OrderDetail> details = new ArrayList<>();
        BigDecimal orderFee = BigDecimal.ZERO;

        for (OrderItemRequest itemReq : request.getItems()) {
            Product product = productMap.get(itemReq.getProductId());
            if (product == null) {
                throw new ResourceNotFoundException("Product not found with ID: " + itemReq.getProductId());
            }

            OrderDetail detail = OrderDetail.builder()
                    .order(order)
                    .product(product)
                    .quantity(itemReq.getQuantity())
                    .unitPrice(product.getPrice())
                    .build();

            details.add(detail);
            
            BigDecimal itemTotal = product.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            orderFee = orderFee.add(itemTotal);
        }

        order.setOrderDetails(details);
        order.setOrderFee(orderFee);
        order.setShippingFee(BigDecimal.ZERO);
        StoreOrderAmountUtils.syncTotalAmount(order);

        StoreOrder savedOrder = storeOrderRepository.save(order);
        return mapToOrderResponse(savedOrder);
    }

    @Override
    public Page<StoreOrderResponse> getMyOrders(String username, OrderStatus status, Pageable pageable) {
        // Keep this for now for backward compatibility or direct use
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));

        if (user.getStore() == null) {
            throw new IllegalArgumentException("User does not belong to any store");
        }

        Long storeId = user.getStore().getStoreId();
        Page<StoreOrder> orderPage;

        if (status != null) {
            orderPage = storeOrderRepository.findByStore_StoreIdAndStatus(storeId, status, pageable);
        } else {
            orderPage = storeOrderRepository.findByStore_StoreId(storeId, pageable);
        }

        return orderPage.map(this::mapToOrderResponse);
    }

    @Override
    public Page<StoreOrderResponse> getAllOrders(OrderStatus status, Pageable pageable) {
        UserContext ctx = SecurityUtils.getCurrentUserContext();
        if (ctx == null) {
            throw new org.springframework.security.access.AccessDeniedException("User context not found or not authenticated");
        }

        Specification<StoreOrder> spec = Specification.where(StoreOrderSpecification.hasStatus(status));

        // Auto-filter based on scope
        if ("STORE".equalsIgnoreCase(ctx.getScope())) {
            spec = spec.and(StoreOrderSpecification.hasStoreId(ctx.getStoreId()));
        } else if (!"SYSTEM".equalsIgnoreCase(ctx.getScope())) {
             // If not SYSTEM and not STORE, we might want to restrict by default or throw error
             // For now, if someone somehow gets here without either scope, we deny
             throw new org.springframework.security.access.AccessDeniedException("Invalid user scope: " + ctx.getScope());
        }
        // If SYSTEM scope, we don't add store filter, showing all orders

        Page<StoreOrder> orderPage = storeOrderRepository.findAll(spec, pageable);
        return orderPage.map(this::mapToOrderResponse);
    }

    @Override
    public StoreOrderResponse getOrderById(Long id) {
        UserContext ctx = SecurityUtils.getCurrentUserContext();
        if (ctx == null) {
            throw new org.springframework.security.access.AccessDeniedException("User context not found");
        }

        StoreOrder order = storeOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + id));

        // Ownership check for STORE scope
        if ("STORE".equalsIgnoreCase(ctx.getScope())) {
            if (!order.getStore().getStoreId().equals(ctx.getStoreId())) {
                throw new org.springframework.security.access.AccessDeniedException("Access denied to this order");
            }
        }

        return mapToOrderResponse(order);
    }

    @Override
    @Transactional
    public StoreOrderResponse updateOrder(Long id, StoreOrderRequest request, String username) {
        StoreOrder order = storeOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + id));

        // BR-07: Modification Window Guard
        if (order.getStatus() != OrderStatus.DRAFT) {
            throw new BusinessRuleViolationException("Cannot modify order because it is already processed (Status: " + order.getStatus() + ")");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getStore() == null || !user.getStore().getStoreId().equals(order.getStore().getStoreId())) {
            throw new org.springframework.security.access.AccessDeniedException("You can only modify orders of your own store");
        }


        // Update details
        order.getOrderDetails().clear();
        BigDecimal orderFee = BigDecimal.ZERO;

        List<Long> productIds = request.getItems().stream()
                .map(com.swp.ckms.dto.request.OrderItemRequest::getProductId)
                .collect(Collectors.toList());

        java.util.Map<Long, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        for (com.swp.ckms.dto.request.OrderItemRequest itemReq : request.getItems()) {
            Product product = productMap.get(itemReq.getProductId());
            if (product == null) {
                throw new ResourceNotFoundException("Product not found");
            }

            OrderDetail detail = OrderDetail.builder()
                    .order(order)
                    .product(product)
                    .quantity(itemReq.getQuantity())
                    .unitPrice(product.getPrice())
                    .build();

            order.getOrderDetails().add(detail);
            orderFee = orderFee.add(product.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity())));
        }

        order.setOrderFee(orderFee);
        StoreOrderAmountUtils.syncTotalAmount(order);
        return mapToOrderResponse(storeOrderRepository.save(order));
    }

    @Override
    @Transactional
    public void cancelOrder(Long id, String username) {

        StoreOrder order = storeOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getStore() == null ||
                !user.getStore().getStoreId().equals(order.getStore().getStoreId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You can only cancel orders of your own store"
            );
        }

        // Guard điều kiện mới
        if (order.getStatus() == OrderStatus.DRAFT) {
            // Hard delete
            storeOrderRepository.delete(order);
            return;
        }

        if (order.getStatus() == OrderStatus.SUBMITTED) {
            order.setStatus(OrderStatus.CANCELLED);
            storeOrderRepository.save(order);
            return;
        }

        // Các trạng thái còn lại không cho hủy
        throw new BusinessRuleViolationException(
                "Cannot cancel an order that has been processed (Current status: "
                        + order.getStatus() + ")"
        );
    }

    @Override
    @Transactional
    public StoreOrderResponse updateOrderStatus(Long id, OrderStatus newStatus) {
        UserContext ctx = SecurityUtils.getCurrentUserContext();
        if (ctx == null) {
            throw new org.springframework.security.access.AccessDeniedException("User context not found");
        }

        // Only SYSTEM scope can approve/reject
        if (!"SYSTEM".equalsIgnoreCase(ctx.getScope())) {
            throw new org.springframework.security.access.AccessDeniedException("Only coordinators can update order status");
        }

        StoreOrder order = storeOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + id));

        // State machine guard: Only SUBMITTED orders can be processed
        if (order.getStatus() != OrderStatus.SUBMITTED) {
            throw new IllegalArgumentException("Order is already processed or in an invalid state for update: " + order.getStatus());
        }

        if (newStatus == OrderStatus.APPROVED) {
            // Anti-double-invoice check
            if (invoiceRepository.existsByOrder_OrderId(id)) {
                throw new IllegalStateException("Invoice already exists for this order");
            }

            User approvedBy = userRepository.findById(ctx.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Approver not found"));

            // Single Kitchen Refactor: Always check capacity against Kitchen ID 1
            CentralKitchen kitchen = centralKitchenRepository.findById(1L)
                    .orElseThrow(() -> new ResourceNotFoundException("Default Kitchen not found with ID: 1"));

            if (kitchen.getMaxDailyCapacity() != null) {
                java.math.BigDecimal existingLoad = storeOrderRepository.sumQuantityByKitchenAndDeliveryDate(
                        1L, order.getDeliveryDate());
                if (existingLoad == null) existingLoad = java.math.BigDecimal.ZERO;

                java.math.BigDecimal newOrderLoad = java.math.BigDecimal.valueOf(
                        order.getOrderDetails().stream().mapToDouble(d -> d.getQuantity()).sum());

                if (existingLoad.add(newOrderLoad).compareTo(kitchen.getMaxDailyCapacity()) > 0) {
                    throw new com.swp.ckms.exception.business.BusinessRuleViolationException(String.format(
                            "Duyệt đơn thất bại: Tổng tải sản xuất ngày %s vượt quá công suất bếp %s (Hệ thống đã nhận: %s, Đơn này: %s)",
                            order.getDeliveryDate(), kitchen.getMaxDailyCapacity(), existingLoad, newOrderLoad));
                }
            }

            order.setStatus(OrderStatus.APPROVED); // Duyệt đơn là đưa vào hàng chờ Scheduled
            order.setApprovedByUser(approvedBy);
            order.setApprovedAt(LocalDateTime.now());

            // Create Invoice
            com.swp.ckms.entity.Invoice invoice = com.swp.ckms.entity.Invoice.builder()
                    .order(order)
                    .amount(order.getTotalAmount())
                    .issuedAt(LocalDateTime.now())
                    .status(InvoiceStatus.PENDING)
                    .build();
            
            invoiceRepository.save(invoice);
            order.setInvoice(invoice); // Link back to the invoice


        } else if (newStatus == OrderStatus.REJECTED) {
            order.setStatus(OrderStatus.REJECTED);
        } else {
            throw new IllegalArgumentException("Invalid target status for approval flow: " + newStatus);
        }

        StoreOrder savedOrder = storeOrderRepository.save(order);

        // --- TRIGGER NOTIFICATION ---
        try {
            String recipient = recipientResolver.resolveStoreManagerEmail(order.getStore().getStoreId(), order.getCreatedByUser().getUserId());
            if (recipient != null) {
                java.util.Map<String, Object> payload = new java.util.HashMap<>();
                payload.put("orderId", savedOrder.getOrderId());
                payload.put("storeName", savedOrder.getStore().getName());
                payload.put("status", savedOrder.getStatus().name());
                payload.put("totalAmount", savedOrder.getTotalAmount());
                payload.put("orderDate", savedOrder.getOrderDate().toString());
                payload.put("dashboardLink", "http://localhost:5173/history");
                
                // Add Items for the table
                List<java.util.Map<String, Object>> items = savedOrder.getOrderDetails().stream()
                        .map(d -> java.util.Map.<String, Object>of(
                                "productName", d.getProduct().getName(),
                                "quantity", d.getQuantity(),
                                "unitPrice", d.getUnitPrice()
                        ))
                        .collect(Collectors.toList());
                payload.put("items", items);
                
                String dedupKey = "ORDER_STATUS_" + savedOrder.getOrderId() + "_" + savedOrder.getStatus().name();
                String template = (savedOrder.getStatus() == OrderStatus.APPROVED) ? "order-approved.html" : "order-rejected.html";
                
                notificationService.createEmailNotification(
                        com.swp.ckms.enums.NotificationType.ORDER_STATUS_CHANGED,
                        recipient,
                        template,
                        payload,
                        dedupKey
                );
            }
        } catch (Exception e) {
            log.error("Failed to trigger order status notification", e);
        }

        return mapToOrderResponse(savedOrder);
    }

    private StoreOrderResponse mapToOrderResponse(StoreOrder order) {
        java.util.Map<Long, AllocationItem> allocationMap = java.util.Collections.emptyMap();
        
        // If order is allocated or further, fetch allocation items for precise display
        if (order.getStatus() == OrderStatus.ALLOCATED || 
            order.getStatus() == OrderStatus.IN_TRANSIT || 
            order.getStatus() == OrderStatus.DELIVERED || 
            order.getStatus() == OrderStatus.CONFIRMED) {
            
            List<AllocationItem> allocationItems = allocationItemRepository.findByOrder_OrderId(order.getOrderId());
            allocationMap = allocationItems.stream()
                    .collect(Collectors.toMap(ai -> ai.getProduct().getId(), ai -> ai));
        }

        final java.util.Map<Long, AllocationItem> finalAllocationMap = allocationMap;
        List<OrderDetailResponse> detailResponses = order.getOrderDetails().stream()
                .map(d -> mapToDetailResponse(d, finalAllocationMap.get(d.getProduct().getId())))
                .collect(Collectors.toList());

        return StoreOrderResponse.builder()
                .orderId(order.getOrderId())
                .storeId(order.getStore().getStoreId())
                .createdByUserId(order.getCreatedByUser().getUserId())
                .orderDate(order.getOrderDate())
                .status(order.getStatus().name())
                .batchId(order.getProductionPlan() != null ? order.getProductionPlan().getPlanId() : order.getBatchId())
                .batchCode(order.getProductionPlan() != null ? order.getProductionPlan().getBatchCode() : null)
            .orderFee(StoreOrderAmountUtils.zeroIfNull(order.getOrderFee()))
            .shippingFee(StoreOrderAmountUtils.zeroIfNull(order.getShippingFee()))
            .totalAmount(StoreOrderAmountUtils.calculateTotalAmount(order.getOrderFee(), order.getShippingFee()))
                .deliveryDate(order.getDeliveryDate())
                .storeName(order.getStore() != null ? order.getStore().getName() : null)
                .storePhone(order.getStore() != null ? order.getStore().getPhoneNumber() : null)
                .orderDetails(detailResponses)
                .build();
    }

    private OrderDetailResponse mapToDetailResponse(OrderDetail detail, AllocationItem allocationItem) {
        Product product = detail.getProduct();
        
        // Use allocated quantity if available, otherwise use original requested quantity
        BigDecimal displayQty = (allocationItem != null) 
                ? allocationItem.getFinalQty() 
                : BigDecimal.valueOf(detail.getQuantity());
                
        BigDecimal subTotal = detail.getUnitPrice().multiply(displayQty);
        
        return OrderDetailResponse.builder()
                .id(detail.getId())
                .productId(product.getId())
                .productName(product.getName())
                .unit(product.getUnit() != null ? product.getUnit().name() : null)
                .quantity(displayQty.intValue()) // Keep as int for DTO compatibility
                .unitPrice(detail.getUnitPrice())
                .subTotal(subTotal)
                .build();
    }

    @Override
    @Transactional
    public StoreOrderResponse submitOrder(Long id, String username) {

        StoreOrder order = storeOrderRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Order not found with ID: " + id)
                );

        if (order.getStatus() != OrderStatus.DRAFT) {
            throw new BusinessRuleViolationException(
                    "Only DRAFT orders can be submitted (Current: " + order.getStatus() + ")"
            );
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found with username: " + username)
                );

        if (user.getStore() == null ||
                !user.getStore().getStoreId().equals(order.getStore().getStoreId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You can only submit your own store orders"
            );
        }

        order.setStatus(OrderStatus.SUBMITTED);

        StoreOrder savedOrder = storeOrderRepository.save(order);

        // --- TRIGGER NOTIFICATION ---
        // Single Kitchen Refactor: Only notify the coordinator of the global kitchen (ID 1)
        try {
            String recipient = recipientResolver.resolveCoordinatorEmail(1L);
            if (recipient != null) {
                java.util.Map<String, Object> payload = java.util.Map.of(
                        "orderId", savedOrder.getOrderId(),
                        "storeName", savedOrder.getStore().getName(),
                        "orderDate", savedOrder.getOrderDate().toString()
                );
                
                notificationService.createEmailNotification(
                        com.swp.ckms.enums.NotificationType.ORDER_SUBMITTED,
                        recipient,
                        "order-submitted.html",
                        payload,
                        "ORDER_SUBMITTED_" + savedOrder.getOrderId()
                );
                log.info("Notification sent to coordinator {} for order #{}", recipient, savedOrder.getOrderId());
            }
        } catch (Exception e) {
            log.error("Failed to trigger order submission notification", e);
        }

        return mapToOrderResponse(savedOrder);
    }

    @Override
    @Transactional
    public StoreOrderResponse rescheduleOrder(Long id, LocalDate newDeliveryDate) {
        UserContext ctx = SecurityUtils.getCurrentUserContext();
        if (ctx == null) {
            throw new org.springframework.security.access.AccessDeniedException("User context not found");
        }

        if (!"SYSTEM".equalsIgnoreCase(ctx.getScope())) {
            throw new org.springframework.security.access.AccessDeniedException("Only coordinators can reschedule orders");
        }

        StoreOrder order = storeOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + id));

        if (order.getStatus() == OrderStatus.LOCKED || order.getStatus() == OrderStatus.ALLOCATED || 
            order.getStatus() == OrderStatus.IN_TRANSIT || order.getStatus() == OrderStatus.DELIVERED || 
            order.getStatus() == OrderStatus.DELIVERY_FAILED || order.getStatus() == OrderStatus.RETURNED ||
            order.getStatus() == OrderStatus.CONFIRMED) {
            throw new BusinessRuleViolationException("Cannot reschedule order in current state: " + order.getStatus());
        }

        if (order.getProductionPlan() != null) {
            throw new BusinessRuleViolationException("Order is currently assigned to a Production Plan. Please remove it from the plan first.");
        }

        if (order.getStatus() == OrderStatus.APPROVED) {
            User approvedBy = userRepository.findById(ctx.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            // Single Kitchen Refactor: Always check against Kitchen ID 1
            CentralKitchen kitchen = centralKitchenRepository.findById(1L).orElse(null);
            
            if (kitchen != null && kitchen.getMaxDailyCapacity() != null) {
                java.math.BigDecimal existingLoad = storeOrderRepository.sumQuantityByKitchenAndDeliveryDate(
                        1L, newDeliveryDate);
                if (existingLoad == null) existingLoad = java.math.BigDecimal.ZERO;

                java.math.BigDecimal orderLoad = java.math.BigDecimal.valueOf(
                        order.getOrderDetails().stream().mapToDouble(d -> d.getQuantity()).sum());

                if (existingLoad.add(orderLoad).compareTo(kitchen.getMaxDailyCapacity()) > 0) {
                    throw new BusinessRuleViolationException(String.format(
                            "Không thể đổi ngày: Tổng tải sản xuất ngày %s sẽ vượt quá công suất bếp %s (Hiện có: %s, Đơn này: %s)",
                            newDeliveryDate, kitchen.getMaxDailyCapacity(), existingLoad, orderLoad));
                }
            }
        }

        log.info("Rescheduling order {} from {} to {}", id, order.getDeliveryDate(), newDeliveryDate);
        order.setDeliveryDate(newDeliveryDate);
        StoreOrder savedOrder = storeOrderRepository.save(order);

        try {
             String recipient = recipientResolver.resolveStoreManagerEmail(order.getStore().getStoreId(), order.getCreatedByUser().getUserId());
             if (recipient != null) {
                 java.util.Map<String, Object> payload = new java.util.HashMap<>();
                 payload.put("orderId", savedOrder.getOrderId());
                 payload.put("newDeliveryDate", savedOrder.getDeliveryDate().toString());
                 payload.put("status", savedOrder.getStatus().name());
                 
                 notificationService.createEmailNotification(
                         com.swp.ckms.enums.NotificationType.ORDER_STATUS_CHANGED,
                         recipient,
                         "order-status-changed.html",
                         payload,
                         "ORDER_RESCHEDULED_" + savedOrder.getOrderId()
                 );
             }
        } catch (Exception e) {
            log.error("Failed to trigger reschedule notification", e);
        }

        return mapToOrderResponse(savedOrder);
    }

    @Override
    @Transactional
    public List<StoreOrderResponse> splitOrder(Long id, List<com.swp.ckms.dto.request.OrderItemRequest> itemsToSplit) {
        UserContext ctx = SecurityUtils.getCurrentUserContext();
        if (ctx == null) throw new org.springframework.security.access.AccessDeniedException("Unauthorized");
        if (!"SYSTEM".equalsIgnoreCase(ctx.getScope())) throw new org.springframework.security.access.AccessDeniedException("Only coordinators can split orders");

        StoreOrder originalOrder = storeOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + id));

        if (originalOrder.getProductionPlan() != null) {
            throw new BusinessRuleViolationException("Cannot split order that is already assigned to a production plan.");
        }

        if (originalOrder.getStatus() != OrderStatus.APPROVED && originalOrder.getStatus() != OrderStatus.SUBMITTED) {
            throw new BusinessRuleViolationException("Only SUBMITTED or APPROVED orders can be split. Current: " + originalOrder.getStatus());
        }

        if (originalOrder.getInvoice() != null && originalOrder.getInvoice().getStatement() != null) {
            throw new BusinessRuleViolationException("Cannot split order as its invoice is already included in a Billing Statement.");
        }

        StoreOrder newOrder = StoreOrder.builder()
                .store(originalOrder.getStore())
                .createdByUser(originalOrder.getCreatedByUser())
                .orderDate(originalOrder.getOrderDate())
                .deliveryDate(originalOrder.getDeliveryDate())
                .status(originalOrder.getStatus())
                .approvedByUser(originalOrder.getApprovedByUser())
                .approvedAt(originalOrder.getApprovedAt())
            .shippingFee(BigDecimal.ZERO)
                .orderDetails(new ArrayList<>())
                .build();

        for (com.swp.ckms.dto.request.OrderItemRequest splitItem : itemsToSplit) {
            OrderDetail originalDetail = originalOrder.getOrderDetails().stream()
                    .filter(d -> d.getProduct().getId().equals(splitItem.getProductId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessRuleViolationException("Product " + splitItem.getProductId() + " not found in original order"));

            if (splitItem.getQuantity() <= 0 || splitItem.getQuantity() >= originalDetail.getQuantity()) {
                throw new BusinessRuleViolationException("Invalid quantity to split for product " + splitItem.getProductId());
            }

            originalDetail.setQuantity(originalDetail.getQuantity() - splitItem.getQuantity());

            OrderDetail newDetail = OrderDetail.builder()
                    .order(newOrder)
                    .product(originalDetail.getProduct())
                    .quantity(splitItem.getQuantity())
                    .unitPrice(originalDetail.getUnitPrice())
                    .build();
            newOrder.getOrderDetails().add(newDetail);
        }

        originalOrder.setOrderFee(calculateOrderFee(originalOrder.getOrderDetails()));
        StoreOrderAmountUtils.syncTotalAmount(originalOrder);
        newOrder.setOrderFee(calculateOrderFee(newOrder.getOrderDetails()));
        StoreOrderAmountUtils.syncTotalAmount(newOrder);

        storeOrderRepository.save(originalOrder);
        StoreOrder savedNewOrder = storeOrderRepository.save(newOrder);

        if (originalOrder.getStatus() == OrderStatus.APPROVED && originalOrder.getInvoice() != null) {
             com.swp.ckms.entity.Invoice oldInvoice = originalOrder.getInvoice();
             oldInvoice.setStatus(com.swp.ckms.enums.InvoiceStatus.CANCELLED);
             invoiceRepository.save(oldInvoice);

             createInvoiceForOrder(originalOrder);
             createInvoiceForOrder(savedNewOrder);
        }

        // Notify Store Manager about the split
        try {
            String recipient = recipientResolver.resolveStoreManagerEmail(originalOrder.getStore().getStoreId(), originalOrder.getCreatedByUser().getUserId());
            if (recipient != null) {
                java.util.Map<String, Object> payload = new java.util.HashMap<>();
                payload.put("originalOrderId", originalOrder.getOrderId());
                payload.put("newOrderId", savedNewOrder.getOrderId());
                payload.put("deliveryDate", originalOrder.getDeliveryDate().toString());
                
                notificationService.createEmailNotification(
                        com.swp.ckms.enums.NotificationType.ORDER_STATUS_CHANGED,
                        recipient,
                        "order-status-changed.html",
                        payload,
                        "ORDER_SPLIT_" + originalOrder.getOrderId()
                );
            }
        } catch (Exception e) {
            log.error("Failed to trigger split notification", e);
        }

        return List.of(mapToOrderResponse(originalOrder), mapToOrderResponse(savedNewOrder));
    }

    private BigDecimal calculateOrderFee(List<OrderDetail> details) {
        return StoreOrderAmountUtils.calculateOrderFeeFromDetails(details);
    }

    private void createInvoiceForOrder(StoreOrder order) {
        com.swp.ckms.entity.Invoice invoice = com.swp.ckms.entity.Invoice.builder()
                .order(order)
                .amount(order.getTotalAmount())
                .issuedAt(LocalDateTime.now())
                .status(com.swp.ckms.enums.InvoiceStatus.PENDING)
                .build();
        invoiceRepository.save(invoice);
        order.setInvoice(invoice);
    }
}
