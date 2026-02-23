package com.swp.ckms.service.impl;

import com.swp.ckms.dto.request.OrderItemRequest;
import com.swp.ckms.dto.request.StoreOrderRequest;
import com.swp.ckms.dto.response.OrderDetailResponse;
import com.swp.ckms.dto.response.StoreOrderResponse;
import com.swp.ckms.entity.*;
import com.swp.ckms.exception.business.ResourceNotFoundException;
import com.swp.ckms.repository.FranchiseStoreRepository;
import com.swp.ckms.repository.ProductRepository;
import com.swp.ckms.repository.StoreOrderRepository;
import com.swp.ckms.repository.UserRepository;
import com.swp.ckms.service.StoreOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        List<OrderDetailResponse> detailResponses = savedOrder.getOrderDetails().stream()
                .map(this::mapToDetailResponse)
                .collect(Collectors.toList());

        return StoreOrderResponse.builder()
                .orderId(savedOrder.getOrderId())
                .storeId(savedOrder.getStore().getStoreId())
                .createdByUserId(savedOrder.getCreatedByUser().getUserId())
                .orderDate(savedOrder.getOrderDate())
                .status(savedOrder.getStatus().name())
                .batchId(savedOrder.getBatchId())
                .totalAmount(savedOrder.getTotalAmount())
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
