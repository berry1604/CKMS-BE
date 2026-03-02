package com.swp.ckms.service.impl;

import com.swp.ckms.dto.request.ConfirmDeliveryRequest;
import com.swp.ckms.dto.request.CreateShipmentRequest;
import com.swp.ckms.dto.response.ShipmentResponse;
import com.swp.ckms.entity.*;
import com.swp.ckms.enums.OrderStatus;
import com.swp.ckms.enums.ShipmentStatus;
import com.swp.ckms.exception.business.ResourceNotFoundException;
import com.swp.ckms.repository.*;
import com.swp.ckms.security.SecurityUtils;
import com.swp.ckms.security.UserContext;
import com.swp.ckms.service.ShipmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ShipmentServiceImpl implements ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final StoreOrderRepository storeOrderRepository;
    private final ProductionPlanRepository productionPlanRepository;
    private final FranchiseStoreRepository franchiseStoreRepository;
    private final UserRepository userRepository;

//-----------------------------------------------------------

    // TÍCH HỢP THÊM AHAMOVE THÌ SẼ THÊM VÀO CHỖ NÀY

//-----------------------------------------------------------
    @Override
    public ShipmentResponse createShipment(CreateShipmentRequest request) {
        UserContext ctx = SecurityUtils.getCurrentUserContext();
        if (ctx == null) throw new AccessDeniedException("Unauthorized");

        User currentUser = userRepository.findById(ctx.getUserId())
                .orElseThrow(() -> new AccessDeniedException("User not found"));

        // Validate production plan exists and is FINISHED
        ProductionPlan plan = productionPlanRepository.findById(request.getProductionPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("Production Plan not found: " + request.getProductionPlanId()));

        if (plan.getStatus() != com.swp.ckms.enums.ProductionPlanStatus.FINISHED) {
            throw new IllegalStateException("Production Plan must be FINISHED before creating shipment. Current: " + plan.getStatus());
        }

        // Validate store
        FranchiseStore store = franchiseStoreRepository.findById(request.getStoreId())
                .orElseThrow(() -> new ResourceNotFoundException("Store not found: " + request.getStoreId()));

        // Validate and fetch store orders
        List<StoreOrder> orders = storeOrderRepository.findAllById(request.getStoreOrderIds());

        if (orders.size() != request.getStoreOrderIds().size()) {
            throw new ResourceNotFoundException("Some store orders not found");
        }

        // Validate all orders belong to the same store and are in READY status
        for (StoreOrder order : orders) {
            if (!order.getStore().getStoreId().equals(store.getStoreId())) {
                throw new IllegalArgumentException("Order #" + order.getOrderId() + " does not belong to store #" + store.getStoreId());
            }
            if (order.getStatus() != OrderStatus.READY) {
                throw new IllegalStateException("Order #" + order.getOrderId() + " is not in READY status. Current: " + order.getStatus());
            }
            if (order.getShipment() != null) {
                throw new IllegalStateException("Order #" + order.getOrderId() + " is already assigned to shipment #" + order.getShipment().getShipmentId());
            }
        }

        // Create shipment
        Shipment shipment = Shipment.builder()
                .store(store)
                .productionPlan(plan)
                .driverName(request.getDriverName())
                .driverPhone(request.getDriverPhone())
                .vehicleInfo(request.getVehicleInfo())
                .shippingFee(request.getShippingFee())
                .status(ShipmentStatus.PENDING)
                .note(request.getNote())
                .createdBy(currentUser)
                .build();

        Shipment savedShipment = shipmentRepository.save(shipment);

        // Assign orders to shipment
        for (StoreOrder order : orders) {
            order.setShipment(savedShipment);
            order.setStatus(OrderStatus.SHIPPING);
        }
        storeOrderRepository.saveAll(orders);

        log.info("Shipment #{} created for store {} with {} orders",
                savedShipment.getShipmentId(), store.getName(), orders.size());

        return mapToResponse(savedShipment, orders);
    }

    @Override
    public ShipmentResponse prepareShipment(Long shipmentId) {
        // Kitchen staff marks shipment as prepared
        Shipment shipment = getShipmentOrThrow(shipmentId);

        if (shipment.getStatus() != ShipmentStatus.PENDING) {
            throw new IllegalStateException("Shipment must be PENDING to prepare. Current: " + shipment.getStatus());
        }

        shipment.setStatus(ShipmentStatus.PREPARED);
        shipmentRepository.save(shipment);

        log.info("Shipment #{} marked as PREPARED", shipmentId);

        return mapToResponse(shipment);
    }
//-----------------------------------------------------------


    //THÊM API AHAMOVE VÀO start Transit

//-----------------------------------------------------------
    @Override
    public ShipmentResponse startTransit(Long shipmentId) {
        // Coordinator confirms shipment is on the way
        Shipment shipment = getShipmentOrThrow(shipmentId);

        if (shipment.getStatus() != ShipmentStatus.PREPARED) {
            throw new IllegalStateException("Shipment must be PREPARED to start transit. Current: " + shipment.getStatus());
        }

        // // === THÊM: Gọi AhaMove tạo đơn giao ===
        // AhaMoveOrderResponse ahaResponse = ahaMoveClient.createOrder(
        //         shipment.getStore(), shipment.getNote());
        // shipment.setAhamoveOrderId(ahaResponse.getOrderId());
        // shipment.setTrackingLink(ahaResponse.getSharedLink());
        // shipment.setShippingFee(BigDecimal.valueOf(ahaResponse.getTotalPay()));
        // // === KẾT THÚC THÊM ===

        shipment.setStatus(ShipmentStatus.IN_TRANSIT);
        shipment.setShippedAt(LocalDateTime.now());
        shipmentRepository.save(shipment);

        log.info("Shipment #{} is now IN_TRANSIT", shipmentId);

        return mapToResponse(shipment);
    }

    @Override
    public ShipmentResponse confirmDelivery(Long shipmentId, ConfirmDeliveryRequest request) {
        // Store staff confirms receiving goods
        UserContext ctx = SecurityUtils.getCurrentUserContext();
        if (ctx == null) throw new AccessDeniedException("Unauthorized");

        User currentUser = userRepository.findById(ctx.getUserId())
                .orElseThrow(() -> new AccessDeniedException("User not found"));

        Shipment shipment = getShipmentOrThrow(shipmentId);

        if (shipment.getStatus() != ShipmentStatus.IN_TRANSIT) {
            throw new IllegalStateException("Shipment must be IN_TRANSIT to confirm delivery. Current: " + shipment.getStatus());
        }

        // Store staff can only confirm their own store's shipment
        if ("STORE".equalsIgnoreCase(ctx.getScope())) {
            if (!shipment.getStore().getStoreId().equals(ctx.getStoreId())) {
                throw new AccessDeniedException("You can only confirm deliveries for your own store");
            }
        }

        // Update shipment
        shipment.setStatus(ShipmentStatus.DELIVERED);
        shipment.setDeliveredAt(LocalDateTime.now());
        shipment.setConfirmedBy(currentUser);

        if (request != null && request.getFeedbackNote() != null) {
            shipment.setNote(shipment.getNote() != null
                    ? shipment.getNote() + " | Feedback: " + request.getFeedbackNote()
                    : "Feedback: " + request.getFeedbackNote());
        }

        shipmentRepository.save(shipment);

        // Update all store orders to COMPLETED
        List<StoreOrder> orders = storeOrderRepository.findByShipment_ShipmentId(shipmentId);
        for (StoreOrder order : orders) {
            order.setStatus(OrderStatus.COMPLETED);
        }
        storeOrderRepository.saveAll(orders);

        // TODO: Cập nhật tồn kho cửa hàng (Store Inventory) ở đây
        // transferToStoreInventory(shipment, orders);

        log.info("Shipment #{} DELIVERED and confirmed by user {}", shipmentId, currentUser.getUsername());

        return mapToResponse(shipment, orders);
    }

    @Override
    public ShipmentResponse cancelShipment(Long shipmentId, String reason) {
        Shipment shipment = getShipmentOrThrow(shipmentId);

        if (shipment.getStatus() == ShipmentStatus.DELIVERED) {
            throw new IllegalStateException("Cannot cancel a DELIVERED shipment");
        }

        // Release orders back to READY
        List<StoreOrder> orders = storeOrderRepository.findByShipment_ShipmentId(shipmentId);
        for (StoreOrder order : orders) {
            order.setShipment(null);
            order.setStatus(OrderStatus.READY);
        }
        storeOrderRepository.saveAll(orders);

        shipment.setStatus(ShipmentStatus.CANCELLED);
        shipment.setCancelledAt(LocalDateTime.now());
        shipment.setNote(shipment.getNote() != null
                ? shipment.getNote() + " | Cancel reason: " + reason
                : "Cancel reason: " + reason);
        shipmentRepository.save(shipment);

        log.info("Shipment #{} CANCELLED. Reason: {}", shipmentId, reason);

        return mapToResponse(shipment);
    }

    @Override
    @Transactional(readOnly = true)
    public ShipmentResponse getShipmentById(Long shipmentId) {
        UserContext ctx = SecurityUtils.getCurrentUserContext();
        if (ctx == null) throw new AccessDeniedException("Unauthorized");

        Shipment shipment = getShipmentOrThrow(shipmentId);

        // Store scope can only see their own shipments
        if ("STORE".equalsIgnoreCase(ctx.getScope())) {
            if (!shipment.getStore().getStoreId().equals(ctx.getStoreId())) {
                throw new ResourceNotFoundException("Shipment not found: " + shipmentId);
            }
        }

        return mapToResponse(shipment);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ShipmentResponse> getShipments(ShipmentStatus status, Pageable pageable) {
        UserContext ctx = SecurityUtils.getCurrentUserContext();
        if (ctx == null) throw new AccessDeniedException("Unauthorized");

        Page<Shipment> page;

        if ("STORE".equalsIgnoreCase(ctx.getScope())) {
            // Store staff only sees their store's shipments
            if (status != null) {
                page = shipmentRepository.findByStore_StoreIdAndStatus(ctx.getStoreId(), status, pageable);
            } else {
                page = shipmentRepository.findByStore_StoreId(ctx.getStoreId(), pageable);
            }
        } else {
            // System scope sees all
            if (status != null) {
                page = shipmentRepository.findByStatus(status, pageable);
            } else {
                page = shipmentRepository.findAll(pageable);
            }
        }

        return page.map(this::mapToResponse);
    }

    // === Private helpers ===

    private Shipment getShipmentOrThrow(Long shipmentId) {
        return shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found: " + shipmentId));
    }

    private ShipmentResponse mapToResponse(Shipment shipment) {
        List<StoreOrder> orders = storeOrderRepository.findByShipment_ShipmentId(shipment.getShipmentId());
        return mapToResponse(shipment, orders);
    }

    private ShipmentResponse mapToResponse(Shipment shipment, List<StoreOrder> orders) {
        return ShipmentResponse.builder()
                .shipmentId(shipment.getShipmentId())
                .storeId(shipment.getStore().getStoreId())
                .storeName(shipment.getStore().getName())
                .productionPlanId(shipment.getProductionPlan() != null ? shipment.getProductionPlan().getPlanId() : null)
                .status(shipment.getStatus().name())
                .driverName(shipment.getDriverName())
                .driverPhone(shipment.getDriverPhone())
                .vehicleInfo(shipment.getVehicleInfo())
                .shippingFee(shipment.getShippingFee())
                .note(shipment.getNote())
                .createdByUserId(shipment.getCreatedBy() != null ? shipment.getCreatedBy().getUserId() : null)
                .createdByUsername(shipment.getCreatedBy() != null ? shipment.getCreatedBy().getUsername() : null)
                .confirmedByUserId(shipment.getConfirmedBy() != null ? shipment.getConfirmedBy().getUserId() : null)
                .confirmedByUsername(shipment.getConfirmedBy() != null ? shipment.getConfirmedBy().getUsername() : null)
                .createdAt(shipment.getCreatedAt())
                .shippedAt(shipment.getShippedAt())
                .deliveredAt(shipment.getDeliveredAt())
                .storeOrderIds(orders.stream().map(StoreOrder::getOrderId).collect(Collectors.toList()))
                .build();
    }
}