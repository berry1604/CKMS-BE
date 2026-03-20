package com.swp.ckms.service.impl;

import com.swp.ckms.dto.request.ConfirmDeliveryRequest;
import com.swp.ckms.dto.request.CreateShipmentRequest;
import com.swp.ckms.dto.response.ShipmentResponse;
import com.swp.ckms.dto.request.CreateShipmentRequest.DropPointRequest;
import com.swp.ckms.enums.ProductionPlanStatus;
import com.swp.ckms.entity.*;
import com.swp.ckms.enums.OrderStatus;
import com.swp.ckms.enums.ShipmentStatus;
import com.swp.ckms.exception.business.ResourceNotFoundException;
import com.swp.ckms.repository.*;
import com.swp.ckms.security.SecurityUtils;
import com.swp.ckms.security.UserContext;
import com.swp.ckms.service.ShipmentService;
import com.swp.ckms.integration.ahamove.shipment.AhamoveShipmentService;
import com.swp.ckms.enums.InventoryTransactionType;
import com.swp.ckms.enums.InvoiceStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final KitchenStockItemRepository kitchenStockItemRepository;
    private final StoreStockItemRepository storeStockItemRepository;
    private final ShipmentSourcingRecordRepository shipmentSourcingRecordRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final KitchenWarehouseRepository kitchenWarehouseRepository;
    private final StoreWarehouseRepository storeWarehouseRepository;
    private final InvoiceRepository invoiceRepository;
    private final AhamoveShipmentService ahamoveShipmentService; // Inject AhamoveShipmentService để gọi khi cần thiết
    private final AllocationItemRepository allocationItemRepository;
    private final com.swp.ckms.service.NotificationService notificationService;
    private final com.swp.ckms.util.RecipientResolver recipientResolver;
    private final ShipmentStopRepository shipmentStopRepository;
    

    @Override
    public ShipmentResponse createShipment(CreateShipmentRequest request) {
        UserContext ctx = SecurityUtils.getCurrentUserContext();
        if (ctx == null) throw new AccessDeniedException("Unauthorized");

        User currentUser = userRepository.findById(ctx.getUserId())
                .orElseThrow(() -> new AccessDeniedException("User not found"));

        // Validate production plan
        ProductionPlan plan = productionPlanRepository.findById(request.getProductionPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("Production Plan not found"));

        if (plan.getStatus() != ProductionPlanStatus.FINISHED) {
            throw new IllegalStateException("Production Plan must be FINISHED to create shipment");
        }

        // Create shipment header, destination stores are now represented only by ShipmentStop entries.
        Shipment shipment = Shipment.builder()
                .productionPlan(plan)
                .ahamoveServiceId(request.getAhamoveServiceId())
                .status(ShipmentStatus.PENDING)
                .remarks(request.getRemarks())
                .createdBy(currentUser)
                .build();

        Shipment savedShipment = shipmentRepository.save(shipment);

        // Create stops (multi-drop points)
        List<ShipmentStop> stops = new ArrayList<>();
        int stopOrder = 1;

        for (DropPointRequest dropPoint : request.getDropPoints()) {
            FranchiseStore store = franchiseStoreRepository.findById(dropPoint.getStoreId())
                    .orElseThrow(() -> new ResourceNotFoundException("Store not found: " + dropPoint.getStoreId()));

            ShipmentStop stop = ShipmentStop.builder()
                    .shipment(savedShipment)
                    .store(store)
                    .stopOrder(stopOrder++)
                    .remarks(dropPoint.getRemarks())
                    .build();

            ShipmentStop savedStop = shipmentStopRepository.save(stop);

            // Validate and assign orders to this stop
            List<StoreOrder> orders = storeOrderRepository.findAllById(dropPoint.getStoreOrderIds());
            
            for (StoreOrder order : orders) {
                if (!order.getStore().getStoreId().equals(store.getStoreId())) {
                    throw new IllegalArgumentException("Order #" + order.getOrderId() 
                            + " does not belong to store #" + store.getStoreId());
                }
                // if (order.getStatus() != OrderStatus.READY) {
                //     throw new IllegalStateException("Order #" + order.getOrderId() + " is not READY");
                // }
                if (order.getShipmentStop() != null) {
                    throw new IllegalStateException("Order #" + order.getOrderId() + " already assigned to a shipment");
                }
                
                order.setShipmentStop(savedStop);
                order.setStatus(OrderStatus.IN_TRANSIT);
            }
            storeOrderRepository.saveAll(orders);
            stops.add(savedStop);
        }

        savedShipment.setStops(stops);

        log.info("Shipment #{} created with {} drop points", 
                savedShipment.getShipmentId(), stops.size());

        return mapToResponse(savedShipment);
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

    @Override
    public ShipmentResponse startTransit(Long shipmentId) {
        // Coordinator confirms shipment is on the way
        Shipment shipment = getShipmentOrThrow(shipmentId);

        if (shipment.getStatus() != ShipmentStatus.PREPARED) {
            throw new IllegalStateException("Shipment must be PREPARED to start transit. Current: " + shipment.getStatus());
        }

        shipment.setStatus(ShipmentStatus.IN_TRANSIT);
        shipment.setShippedAt(LocalDateTime.now());
        
        sourceAndDeductStock(shipment);

        shipmentRepository.save(shipment);
        try {
            ahamoveShipmentService.dispatchToAhamove(shipment);
        } catch (Exception e) {
            log.error("Lỗi khi dispatch shipment #{} lên AhaMove: {}", shipmentId, e.getMessage());
        }

        // --- TRIGGER NOTIFICATION ---
        try {
            Set<Long> notifiedStoreIds = new HashSet<>();
            for (ShipmentStop stop : shipment.getStops()) {
                if (stop.getStore() == null || !notifiedStoreIds.add(stop.getStore().getStoreId())) {
                    continue;
                }

                String recipient = recipientResolver.resolveStoreManagerEmail(stop.getStore().getStoreId(),
                        shipment.getCreatedBy() != null ? shipment.getCreatedBy().getUserId() : null);
                if (recipient == null) {
                    continue;
                }

                java.util.Map<String, Object> payload = new java.util.HashMap<>();
                payload.put("shipmentId", shipment.getShipmentId());
                payload.put("storeName", stop.getStore().getName());
                payload.put("driverName", shipment.getDriverName());
                payload.put("driverPhone", shipment.getDriverPhone());
                payload.put("vehicleInfo", shipment.getVehicleInfo());
                payload.put("trackingLink", "http://localhost:5173/shipments/" + shipment.getShipmentId());

                notificationService.createEmailNotification(
                        com.swp.ckms.enums.NotificationType.SHIPMENT_STARTED,
                        recipient,
                        "shipment-started.html",
                        payload,
                        "SHIPMENT_STARTED_" + shipment.getShipmentId() + "_STORE_" + stop.getStore().getStoreId()
                );
            }
        } catch (Exception e) {
            log.error("Failed to trigger shipment started notification", e);
        }

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
            if (!hasStoreAccess(shipment, ctx.getStoreId())) {
                throw new AccessDeniedException("You can only confirm deliveries for your own store");
            }
        }

        // Update shipment
        shipment.setStatus(ShipmentStatus.DELIVERED);
        shipment.setDeliveredAt(LocalDateTime.now());
        shipment.setConfirmedBy(currentUser);

        if (request != null && request.getFeedbackNote() != null) {
            shipment.setRemarks(shipment.getRemarks() != null
                    ? shipment.getRemarks() + " | Feedback: " + request.getFeedbackNote()
                    : "Feedback: " + request.getFeedbackNote());
        }

        shipmentRepository.save(shipment);

        // Update all store orders to DELIVERED
        List<StoreOrder> orders = storeOrderRepository.findByShipmentStop_Shipment_ShipmentId(shipment.getShipmentId());
        for (StoreOrder order : orders) {
            order.setStatus(OrderStatus.DELIVERED);
        }
        storeOrderRepository.saveAll(orders);

        // --- LOGIC CỘNG KHO STORE & UPDATE INVOICE ---
        confirmAndTransferStock(shipment);
        // ----------------------------------------------

        log.info("Shipment #{} DELIVERED and confirmed by user {}", shipmentId, currentUser.getUsername());

        return mapToResponse(shipment, orders);
    }

    @Override
    public ShipmentResponse cancelShipment(Long shipmentId, String reason) {
        Shipment shipment = getShipmentOrThrow(shipmentId);

        if (shipment.getStatus() == ShipmentStatus.DELIVERED
                || shipment.getStatus() == ShipmentStatus.RETURNED
                || shipment.getStatus() == ShipmentStatus.DELIVERY_FAILED) {
            throw new IllegalStateException("Cannot cancel a completed shipment. Current: " + shipment.getStatus());
        }
        // Hủy đơn trên AhaMove nếu đã tạo
        ahamoveShipmentService.cancelAhamoveOrder(shipment, reason); 
        // Release orders back to READY
        List<StoreOrder> orders = storeOrderRepository.findByShipmentStop_Shipment_ShipmentId(shipment.getShipmentId());
        for (StoreOrder order : orders) {

            order.setShipmentStop(null);
            order.setStatus(OrderStatus.READY);
        }
        storeOrderRepository.saveAll(orders);

        shipment.setStatus(ShipmentStatus.CANCELLED);
        shipment.setCancelledAt(LocalDateTime.now());
        shipment.setRemarks(shipment.getRemarks() != null
                ? shipment.getRemarks() + " | Cancel reason: " + reason
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
            if (!hasStoreAccess(shipment, ctx.getStoreId())) {
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
                page = shipmentRepository.findDistinctByStops_Store_StoreIdAndStatus(ctx.getStoreId(), status, pageable);
            } else {
                page = shipmentRepository.findDistinctByStops_Store_StoreId(ctx.getStoreId(), pageable);
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
        List<StoreOrder> orders = storeOrderRepository.findByShipmentStop_Shipment_ShipmentId(shipment.getShipmentId());
        return mapToResponse(shipment, orders);
    }

    private ShipmentResponse mapToResponse(Shipment shipment, List<StoreOrder> orders) {
        Map<Long, List<Long>> orderIdsByStopId = orders.stream()
            .filter(order -> order.getShipmentStop() != null)
            .collect(Collectors.groupingBy(
                order -> order.getShipmentStop().getStopId(),
                Collectors.mapping(StoreOrder::getOrderId, Collectors.toList())
            ));

        List<ShipmentResponse.StopResponse> stopResponses = shipment.getStops() == null
            ? List.of()
            : shipment.getStops().stream()
                .sorted(Comparator.comparing(ShipmentStop::getStopOrder))
                .map(stop -> ShipmentResponse.StopResponse.builder()
                    .stopId(stop.getStopId())
                    .stopOrder(stop.getStopOrder())
                    .storeId(stop.getStore() != null ? stop.getStore().getStoreId() : null)
                    .storeName(stop.getStore() != null ? stop.getStore().getName() : null)
                    .remarks(stop.getRemarks())
                    .storeOrderIds(orderIdsByStopId.getOrDefault(stop.getStopId(), List.of()))
                    .build())
                .collect(Collectors.toList());

        return ShipmentResponse.builder()
                .shipmentId(shipment.getShipmentId())
            .stops(stopResponses)
                .productionPlanId(shipment.getProductionPlan() != null ? shipment.getProductionPlan().getPlanId() : null)
                .status(shipment.getStatus().name())
                .ahamoveOrderId(shipment.getAhamoveOrderId())
                .trackingLink(shipment.getTrackingLink())
                .ahamoveStatus(shipment.getAhamoveStatus())
                .driverName(shipment.getDriverName())
                .driverPhone(shipment.getDriverPhone())
                .vehicleInfo(shipment.getVehicleInfo())
                .shippingFee(shipment.getShippingFee())
                .remarks(shipment.getRemarks())
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

    private void sourceAndDeductStock(Shipment shipment) {
        log.info("Starting FIFO sourcing for shipment #{}", shipment.getShipmentId());

        // 1. Aggregate Demand
        List<StoreOrder> orders = storeOrderRepository.findByShipmentStop_Shipment_ShipmentId(shipment.getShipmentId());
        java.util.Map<Long, java.math.BigDecimal> productDemands = new java.util.HashMap<>();

        for (StoreOrder order : orders) {
            for (OrderDetail detail : order.getOrderDetails()) {
                Long productId = detail.getProduct().getId();
                java.math.BigDecimal qty = java.math.BigDecimal.valueOf(detail.getQuantity());
                productDemands.merge(productId, qty, java.math.BigDecimal::add);
            }
        }

        if (productDemands.isEmpty()) return;

        // 2. Lock & Sourcing from Kitchen Warehouse (Assume 1st warehouse for simplicity or specific one)
        KitchenWarehouse kitchenWarehouse = kitchenWarehouseRepository.findById(1L)
                .orElseThrow(() -> new ResourceNotFoundException("Central Kitchen Warehouse not found"));

        List<Long> productIds = new java.util.ArrayList<>(productDemands.keySet());
        List<KitchenStockItem> stocks = kitchenStockItemRepository.lockProductsForDeduction(kitchenWarehouse.getWarehouseId(), productIds);

        // 3. FIFO Logic
        for (java.util.Map.Entry<Long, java.math.BigDecimal> entry : productDemands.entrySet()) {
            Long productId = entry.getKey();
            java.math.BigDecimal remainingDemand = entry.getValue();

            List<KitchenStockItem> productStocks = stocks.stream()
                    .filter(s -> s.getProduct().getId().equals(productId))
                    .collect(java.util.stream.Collectors.toList());

            for (KitchenStockItem stock : productStocks) {
                if (remainingDemand.compareTo(java.math.BigDecimal.ZERO) <= 0) break;

                java.math.BigDecimal deductQty = stock.getQuantity().min(remainingDemand);
                stock.setQuantity(stock.getQuantity().subtract(deductQty));
                remainingDemand = remainingDemand.subtract(deductQty);

                // Create Sourcing Record for traceability
                ShipmentSourcingRecord record = ShipmentSourcingRecord.builder()
                        .shipment(shipment)
                        .product(stock.getProduct())
                        .quantity(deductQty)
                        .expiryDate(stock.getExpiryDate() != null ? stock.getExpiryDate() : java.time.LocalDate.now().plusDays(3))
                        .productionPlan(stock.getProductionPlan())
                        .build();
                shipmentSourcingRecordRepository.save(record);

                // Log Transaction
                InventoryTransaction transaction = InventoryTransaction.builder()
                        .product(stock.getProduct())
                        .quantity(deductQty.negate())
                        .type(InventoryTransactionType.SHIPMENT_OUT)
                        .refId(shipment.getShipmentId())
                        .kitchenWarehouse(kitchenWarehouse)
                        .expiryDate(stock.getExpiryDate())
                        .productionPlan(stock.getProductionPlan())
                        .createdAt(LocalDateTime.now())
                        .build();
                inventoryTransactionRepository.save(transaction);
            }

            if (remainingDemand.compareTo(java.math.BigDecimal.ZERO) > 0) {
                throw new IllegalStateException("Insufficient stock in kitchen for product ID: " + productId + ". Missing: " + remainingDemand);
            }
        }
        kitchenStockItemRepository.saveAll(stocks);
    }

    private void confirmAndTransferStock(Shipment shipment) {
        log.info("Transferring sourced stock to stores for shipment #{}", shipment.getShipmentId());

        List<StoreOrder> orders = storeOrderRepository.findByShipmentStop_Shipment_ShipmentId(shipment.getShipmentId());
        List<ShipmentSourcingRecord> records = shipmentSourcingRecordRepository.findByShipment_ShipmentId(shipment.getShipmentId());

        Map<Long, Map<Long, java.math.BigDecimal>> storeProductDemand = new HashMap<>();
        for (StoreOrder order : orders) {
            Long storeId = order.getStore().getStoreId();
            Map<Long, java.math.BigDecimal> productDemand = storeProductDemand.computeIfAbsent(storeId, key -> new HashMap<>());
            for (OrderDetail detail : order.getOrderDetails()) {
                Long productId = detail.getProduct().getId();
                java.math.BigDecimal quantity = java.math.BigDecimal.valueOf(detail.getQuantity());
                productDemand.merge(productId, quantity, java.math.BigDecimal::add);
            }
        }

        Map<Long, List<ShipmentSourcingRecord>> recordsByProduct = records.stream()
                .collect(Collectors.groupingBy(record -> record.getProduct().getId()));
        recordsByProduct.values().forEach(productRecords -> productRecords.sort(
                Comparator.comparing(ShipmentSourcingRecord::getExpiryDate,
                        Comparator.nullsLast(Comparator.naturalOrder()))
        ));

        Map<Long, java.math.BigDecimal> remainingByRecord = records.stream()
                .collect(Collectors.toMap(ShipmentSourcingRecord::getId, ShipmentSourcingRecord::getQuantity));

        Map<Long, StoreWarehouse> warehouseByStoreId = new HashMap<>();
        for (Map.Entry<Long, Map<Long, java.math.BigDecimal>> storeDemandEntry : storeProductDemand.entrySet()) {
            Long storeId = storeDemandEntry.getKey();
            StoreWarehouse storeWarehouse = warehouseByStoreId.computeIfAbsent(storeId,
                    key -> storeWarehouseRepository.findByStore_StoreId(key)
                            .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found for store ID: " + key)));

            for (Map.Entry<Long, java.math.BigDecimal> productDemandEntry : storeDemandEntry.getValue().entrySet()) {
                Long productId = productDemandEntry.getKey();
                java.math.BigDecimal remainingDemand = productDemandEntry.getValue();

                List<ShipmentSourcingRecord> productRecords = recordsByProduct.getOrDefault(productId, List.of());
                for (ShipmentSourcingRecord record : productRecords) {
                    if (remainingDemand.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                        break;
                    }

                    java.math.BigDecimal remainingRecordQty = remainingByRecord.getOrDefault(record.getId(), java.math.BigDecimal.ZERO);
                    if (remainingRecordQty.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                        continue;
                    }

                    java.math.BigDecimal transferQty = remainingDemand.min(remainingRecordQty);
                    remainingByRecord.put(record.getId(), remainingRecordQty.subtract(transferQty));
                    remainingDemand = remainingDemand.subtract(transferQty);

                    StoreStockItem storeStock = StoreStockItem.builder()
                            .warehouse(storeWarehouse)
                            .product(record.getProduct())
                            .quantity(transferQty)
                            .expiryDate(record.getExpiryDate())
                            .productionPlan(record.getProductionPlan())
                            .build();
                    storeStockItemRepository.save(storeStock);

                    InventoryTransaction transaction = InventoryTransaction.builder()
                            .product(record.getProduct())
                            .quantity(transferQty)
                            .type(InventoryTransactionType.SHIPMENT_IN)
                            .refId(shipment.getShipmentId())
                            .storeWarehouse(storeWarehouse)
                            .expiryDate(record.getExpiryDate())
                            .productionPlan(record.getProductionPlan())
                            .createdAt(LocalDateTime.now())
                            .build();
                    inventoryTransactionRepository.save(transaction);
                }

                if (remainingDemand.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    throw new IllegalStateException("Insufficient sourced stock for product ID " + productId
                            + " at store ID " + storeId + ". Missing: " + remainingDemand);
                }
            }
        }

        // --- UPDATE INVOICE STATUS ---
        for (StoreOrder order : orders) {
            if (order.getInvoice() != null) {
                Invoice invoice = order.getInvoice();
                invoice.setStatus(InvoiceStatus.FULFILLED);
                invoiceRepository.save(invoice);
            }
        }
    }

    private boolean hasStoreAccess(Shipment shipment, Long storeId) {
        if (storeId == null || shipment.getStops() == null) {
            return false;
        }
        return shipment.getStops().stream()
                .map(ShipmentStop::getStore)
                .filter(java.util.Objects::nonNull)
                .anyMatch(store -> storeId.equals(store.getStoreId()));
    }
}