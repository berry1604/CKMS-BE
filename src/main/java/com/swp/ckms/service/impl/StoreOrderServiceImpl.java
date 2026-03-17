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
import com.swp.ckms.enums.InvoiceStatus;
import com.swp.ckms.enums.OrderStatus;
import com.swp.ckms.exception.business.ResourceNotFoundException;
import com.swp.ckms.repository.FranchiseStoreRepository;
import com.swp.ckms.repository.InvoiceRepository;
import com.swp.ckms.repository.ProductRepository;
import com.swp.ckms.repository.StoreOrderRepository;
import com.swp.ckms.repository.StoreStockItemRepository;
import com.swp.ckms.repository.StoreWarehouseRepository;
import com.swp.ckms.repository.UserRepository;
import com.swp.ckms.repository.specification.StoreOrderSpecification;
import com.swp.ckms.entity.StoreWarehouse;
import com.swp.ckms.exception.business.BusinessRuleViolationException;
import com.swp.ckms.security.SecurityUtils;
import com.swp.ckms.security.UserContext;
import com.swp.ckms.service.StoreOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
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
    private final FranchiseStoreRepository franchiseStoreRepository;
    private final ProductRepository productRepository;
    private final InvoiceRepository invoiceRepository;
    private final StoreWarehouseRepository storeWarehouseRepository;
    private final StoreStockItemRepository storeStockItemRepository;
    private final com.swp.ckms.service.NotificationService notificationService;
    private final com.swp.ckms.util.RecipientResolver recipientResolver;

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
        BigDecimal totalAmount = BigDecimal.ZERO;

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
            totalAmount = totalAmount.add(itemTotal);
        }

        order.setOrderDetails(details);
        order.setTotalAmount(totalAmount);

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
        BigDecimal totalAmount = BigDecimal.ZERO;

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
            totalAmount = totalAmount.add(product.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity())));
        }

        order.setTotalAmount(totalAmount);
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

            // [Phase 2] Kitchen Capacity Guard (Refined: Coordinator-based)
            CentralKitchen kitchen = approvedBy.getKitchen();
            if (kitchen != null && kitchen.getMaxDailyCapacity() != null) {
                java.math.BigDecimal existingLoad = storeOrderRepository.sumQuantityByKitchenAndDeliveryDate(
                        kitchen.getKitchenId(), order.getDeliveryDate());
                if (existingLoad == null) existingLoad = java.math.BigDecimal.ZERO;

                java.math.BigDecimal newOrderLoad = java.math.BigDecimal.valueOf(
                        order.getOrderDetails().stream().mapToDouble(d -> d.getQuantity()).sum());

                if (existingLoad.add(newOrderLoad).compareTo(kitchen.getMaxDailyCapacity()) > 0) {
                    throw new com.swp.ckms.exception.business.BusinessRuleViolationException(String.format(
                            "Duyệt đơn thất bại: Tổng tải sản xuất ngày %s vượt quá công suất bếp %s (Hệ thống đã nhận: %s, Đơn này: %s)",
                            order.getDeliveryDate(), kitchen.getMaxDailyCapacity(), existingLoad, newOrderLoad));
                }
            } else {
                log.info("Skipping capacity guard Check: Kitchen or maxDailyCapacity is not set for coordinator: {}", approvedBy.getUsername());
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
        List<OrderDetailResponse> detailResponses = order.getOrderDetails().stream()
                .map(this::mapToDetailResponse)
                .collect(Collectors.toList());

        return StoreOrderResponse.builder()
                .orderId(order.getOrderId())
                .storeId(order.getStore().getStoreId())
                .createdByUserId(order.getCreatedByUser().getUserId())
                .orderDate(order.getOrderDate())
                .status(order.getStatus().name())
                .batchId(order.getBatchId())
                .totalAmount(order.getTotalAmount())
                .orderDetails(detailResponses)
                .build();
    }

    private OrderDetailResponse mapToDetailResponse(OrderDetail detail) {
        Product product = detail.getProduct();
        BigDecimal subTotal = detail.getUnitPrice().multiply(BigDecimal.valueOf(detail.getQuantity()));
        
        return OrderDetailResponse.builder()
                .id(detail.getId())
                .productId(product.getId())
                .productName(product.getName())
                .unit(product.getUnit() != null ? product.getUnit().name() : null)
                .quantity(detail.getQuantity())
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
            }
        } catch (Exception e) {
            log.error("Failed to trigger order submission notification", e);
        }

        return mapToOrderResponse(savedOrder);
    }
}
