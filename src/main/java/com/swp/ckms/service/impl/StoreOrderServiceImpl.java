package com.swp.ckms.service.impl;

import com.swp.ckms.dto.request.OrderItemRequest;
import com.swp.ckms.dto.request.StoreOrderRequest;
import com.swp.ckms.dto.response.OrderDetailResponse;
import com.swp.ckms.dto.response.StoreOrderResponse;
import com.swp.ckms.entity.FranchiseStore;
import com.swp.ckms.entity.OrderDetail;
import com.swp.ckms.entity.Product;
import com.swp.ckms.entity.StoreOrder;
import com.swp.ckms.entity.User;
import com.swp.ckms.enums.OrderStatus;
import com.swp.ckms.exception.business.ResourceNotFoundException;
import com.swp.ckms.repository.FranchiseStoreRepository;
import com.swp.ckms.repository.ProductRepository;
import com.swp.ckms.repository.StoreOrderRepository;
import com.swp.ckms.repository.UserRepository;
import com.swp.ckms.service.StoreOrderService;
import lombok.RequiredArgsConstructor;
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
@Transactional
public class StoreOrderServiceImpl implements StoreOrderService {

    private final StoreOrderRepository storeOrderRepository;
    private final UserRepository userRepository;
    private final FranchiseStoreRepository franchiseStoreRepository;
    private final ProductRepository productRepository;

    @Override
    public StoreOrderResponse createOrder(StoreOrderRequest request, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));

        FranchiseStore store = franchiseStoreRepository.findById(request.getStoreId())
                .orElseThrow(() -> new ResourceNotFoundException("Store not found with ID: " + request.getStoreId()));

        if (user.getStore() == null || !user.getStore().getStoreId().equals(store.getStoreId())) {
            throw new IllegalArgumentException("User does not belong to the requested store");
        }

        StoreOrder order = StoreOrder.builder()
                .store(store)
                .createdByUser(user)
                .orderDate(LocalDateTime.now())
                .status(OrderStatus.SUBMITTED)
                .batchId(null)
                .build();

        List<OrderDetail> details = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + itemReq.getProductId()));

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
    public Page<StoreOrderResponse> getAllOrdersByStatus(OrderStatus status, Pageable pageable) {
        Page<StoreOrder> orderPage = storeOrderRepository.findByStatus(status, pageable);
        return orderPage.map(this::mapToOrderResponse);
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
}
