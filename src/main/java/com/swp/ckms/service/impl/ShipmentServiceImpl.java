package com.swp.ckms.service.impl;

import com.swp.ckms.dto.request.ConfirmDeliveryRequest;
import com.swp.ckms.dto.request.CreateShipmentRequest;
import com.swp.ckms.dto.response.ShipmentResponse;
import com.swp.ckms.dto.request.CreateShipmentRequest.DropPointRequest;
import com.swp.ckms.enums.ProductionPlanStatus;
import com.swp.ckms.entity.*;
import com.swp.ckms.enums.ShipmentStopStatus;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final ShipmentStopRepository shipmentStopRepository;
    private final ShipmentFeeAllocationService shipmentFeeAllocationService;
    

    @Override
    @org.springframework.transaction.annotation.Transactional
    public ShipmentResponse createShipment(CreateShipmentRequest request) {
        log.info("--- BẮT ĐẦU TẠO SHIPMENT ---");
        log.info("Production Plan ID: {}", request.getProductionPlanId());
        log.info("Tổng số điểm giao hàng (Stops): {}", request.getDropPoints().size());

        UserContext ctx = SecurityUtils.getCurrentUserContext();
        if (ctx == null) throw new AccessDeniedException("Unauthorized");

        User currentUser = userRepository.findById(ctx.getUserId())
                .orElseThrow(() -> new AccessDeniedException("User not found"));

        // Validate production plan
        ProductionPlan plan = productionPlanRepository.findById(request.getProductionPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("Production Plan not found"));

        if (plan.getStatus() != ProductionPlanStatus.FINISHED) {
            log.error("Lỗi: Production Plan {} chưa FINISHED (Status: {})", plan.getPlanId(), plan.getStatus());
            throw new IllegalStateException("Production Plan must be FINISHED to create shipment");
        }

        log.info("1. Lưu Shipment header (Chưa gán Stops)...");
        Shipment shipment = Shipment.builder()
                .productionPlan(plan)
                .ahamoveServiceId(request.getAhamoveServiceId())
                .status(ShipmentStatus.PENDING)
                .remarks(request.getRemarks())
                .createdBy(currentUser)
                .build();

        Shipment savedShipment = shipmentRepository.save(shipment);
        log.info("-> Đã lưu Shipment ID: {}, bắt đầu vòng lặp tạo ShipmentStop", savedShipment.getShipmentId());

        List<ShipmentStop> stops = new ArrayList<>();
        int stopOrder = 1;

        for (DropPointRequest dropPoint : request.getDropPoints()) {
            log.info("2. Đang xử lý Stop thứ {} cho Store ID: {}", stopOrder, dropPoint.getStoreId());
            FranchiseStore store = franchiseStoreRepository.findById(dropPoint.getStoreId())
                    .orElseThrow(() -> new ResourceNotFoundException("Store not found: " + dropPoint.getStoreId()));

            ShipmentStop stop = ShipmentStop.builder()
                    .shipment(savedShipment)
                    .store(store)
                    .stopOrder(stopOrder++)
                    .status(ShipmentStopStatus.PENDING)
                    .remarks(dropPoint.getRemarks())
                    .build();

            ShipmentStop savedStop = shipmentStopRepository.save(stop);
            log.info("-> Đã lưu ShipmentStop ID: {} (Store {})", savedStop.getStopId(), store.getStoreId());

            log.info("3. Bắt đầu validate và gán các đơn hàng (StoreOrder IDs: {})", dropPoint.getStoreOrderIds());
            List<StoreOrder> orders = storeOrderRepository.findAllById(dropPoint.getStoreOrderIds());
            
            for (StoreOrder order : orders) {
                log.info("- Kiểm tra Order ID: {} (Thuộc Store {})", order.getOrderId(), order.getStore().getStoreId());

                if (!order.getStore().getStoreId().equals(store.getStoreId())) {
                    log.error("=> Lỗi: Order #{} không thuộc Store #{} (Nó thuộc Store #{})", 
                              order.getOrderId(), store.getStoreId(), order.getStore().getStoreId());
                    throw new IllegalArgumentException("Order #" + order.getOrderId() 
                            + " does not belong to store #" + store.getStoreId());
                }
                if (order.getStatus() != OrderStatus.READY) {
                    log.error("=> Lỗi: Order #{} chưa ở trạng thái READY (Status hiện tại: {})", 
                              order.getOrderId(), order.getStatus());
                    throw new IllegalStateException("Order #" + order.getOrderId() + " must be READY to be assigned to a shipment");
                }
                
                if (order.getShipmentStop() != null) {
                    log.error("=> Lỗi: Order #{} đã được gán vào ShipmentStop #{}", 
                              order.getOrderId(), order.getShipmentStop().getStopId());
                    throw new IllegalStateException("Order #" + order.getOrderId() + " already assigned to a shipment");
                }
                
                order.setShipmentStop(savedStop);
                order.setStatus(OrderStatus.IN_TRANSIT);
            }
            storeOrderRepository.saveAll(orders);
            stops.add(savedStop);
            log.info("-> Gán thành công {} orders vào Stop ID: {}", orders.size(), savedStop.getStopId());
        }

        savedShipment.setStops(stops);
        log.info("--- HOÀN TẤT TẠO SHIPMENT #{} VỚI {} STOPS ---", savedShipment.getShipmentId(), stops.size());

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
        if (shipment.getStops() != null) {
            for (ShipmentStop stop : shipment.getStops()) {
                if (stop.getStatus() == ShipmentStopStatus.PENDING) {
                    stop.setStatus(ShipmentStopStatus.IN_TRANSIT);
                }
            }
        }
        
        sourceAndDeductStock(shipment);

        shipmentRepository.save(shipment);
        try {
            ahamoveShipmentService.dispatchToAhamove(shipment);
        } catch (Exception e) {
            log.error("Lỗi khi dispatch shipment #{} lên AhaMove: {}", shipmentId, e.getMessage());
        }

    
        log.info("Shipment #{} is now IN_TRANSIT", shipmentId);

        return mapToResponse(shipment);
    }

    @Override
    public ShipmentResponse confirmDelivery(Long shipmentId, Long stopId, ConfirmDeliveryRequest request) {
        UserContext ctx = SecurityUtils.getCurrentUserContext();
        if (ctx == null) throw new AccessDeniedException("Unauthorized");

        User currentUser = userRepository.findById(ctx.getUserId())
                .orElseThrow(() -> new AccessDeniedException("User not found"));

        Shipment shipment = getShipmentOrThrow(shipmentId);

        if (shipment.getStatus() != ShipmentStatus.ARRIVED) {
            throw new IllegalStateException("Shipment must be in ARRIVED status to confirm delivery. Current: " + shipment.getStatus());
        }

        ShipmentStop stop = getStopOrThrow(shipment, stopId);
        if (stop.getStatus() == ShipmentStopStatus.DELIVERED) {
            return mapToResponse(shipment);
        }

        if ("STORE".equalsIgnoreCase(ctx.getScope())) {
            if (!hasStoreAccessToStop(stop, ctx.getStoreId())) {
                throw new AccessDeniedException("You can only confirm deliveries for your own store stop");
            }
        }

        stop.setStatus(ShipmentStopStatus.DELIVERED);
        stop.setDeliveredAt(LocalDateTime.now());
        stop.setConfirmedBy(currentUser);

        if (request != null && request.getFeedbackNote() != null) {
            stop.setRemarks(stop.getRemarks() != null
                    ? stop.getRemarks() + " | Feedback: " + request.getFeedbackNote()
                    : "Feedback: " + request.getFeedbackNote());
        }

        List<StoreOrder> stopOrders = storeOrderRepository.findByShipmentStop_StopId(stop.getStopId());
        for (StoreOrder order : stopOrders) {
            order.setStatus(OrderStatus.DELIVERED);
        }
        storeOrderRepository.saveAll(stopOrders);

        confirmAndTransferStockForStop(shipment, stop, stopOrders);

        if (isAllStopsDelivered(shipment)) {
            shipment.setStatus(ShipmentStatus.DELIVERED);
            shipment.setDeliveredAt(LocalDateTime.now());
            shipment.setConfirmedBy(currentUser);
            shipmentFeeAllocationService.allocateForDeliveredShipment(shipment);
        }

        shipmentRepository.save(shipment);

        log.info("Shipment #{} stop #{} confirmed by user {}", shipmentId, stopId, currentUser.getUsername());

        return mapToResponse(shipment);
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

        releaseUndeliveredOrders(shipment, ShipmentStopStatus.CANCELLED);
        restoreKitchenStockForUndelivered(shipment, "Cancelled shipment: " + reason);

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
            // Store staff works at the stop level, not shipment level
            if (status != null) {
                ShipmentStopStatus stopStatus = mapToStopStatus(status);
                if (stopStatus != null) {
                    page = shipmentRepository.findByStoreIdAndStopStatus(ctx.getStoreId(), stopStatus, pageable);
                } else {
                    page = shipmentRepository.findDistinctByStops_Store_StoreIdAndStatus(ctx.getStoreId(), status, pageable);
                }
            } else {
                // Default: Only show what is ready to be received (ARRIVED)
                page = shipmentRepository.findByStoreIdAndStopStatus(ctx.getStoreId(), ShipmentStopStatus.ARRIVED, pageable);
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
                    .status(stop.getStatus() != null ? stop.getStatus().name() : null)
                    .deliveredAt(stop.getDeliveredAt())
                    .confirmedByUserId(stop.getConfirmedBy() != null ? stop.getConfirmedBy().getUserId() : null)
                    .confirmedByUsername(stop.getConfirmedBy() != null ? stop.getConfirmedBy().getUsername() : null)
                    .storeId(stop.getStore() != null ? stop.getStore().getStoreId() : null)
                    .storeName(stop.getStore() != null ? stop.getStore().getName() : null)
                    .storePhone(stop.getStore() != null ? stop.getStore().getPhoneNumber() : null)
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

    private void confirmAndTransferStockForStop(Shipment shipment, ShipmentStop stop, List<StoreOrder> stopOrders) {
        if (stopOrders == null || stopOrders.isEmpty()) {
            return;
        }

        Map<Long, BigDecimal> demandByProduct = new HashMap<>();
        for (StoreOrder order : stopOrders) {
            for (OrderDetail detail : order.getOrderDetails()) {
                demandByProduct.merge(
                        detail.getProduct().getId(),
                        BigDecimal.valueOf(detail.getQuantity()),
                        BigDecimal::add
                );
            }
        }

        List<ShipmentSourcingRecord> records = shipmentSourcingRecordRepository.findByShipment_ShipmentId(shipment.getShipmentId());
        Map<Long, List<ShipmentSourcingRecord>> recordsByProduct = records.stream()
                .collect(Collectors.groupingBy(record -> record.getProduct().getId()));
        recordsByProduct.values().forEach(productRecords -> productRecords.sort(
                Comparator.comparing(ShipmentSourcingRecord::getExpiryDate,
                        Comparator.nullsLast(Comparator.naturalOrder()))
        ));

        List<InventoryTransaction> alreadyInTransactions = inventoryTransactionRepository
                .findByRefIdAndType(shipment.getShipmentId(), InventoryTransactionType.SHIPMENT_IN);
        Map<Long, BigDecimal> alreadyTransferredByProduct = new HashMap<>();
        for (InventoryTransaction tx : alreadyInTransactions) {
            if (tx.getProduct() == null) {
                continue;
            }
            alreadyTransferredByProduct.merge(tx.getProduct().getId(), tx.getQuantity(), BigDecimal::add);
        }

        Long storeId = stop.getStore() != null ? stop.getStore().getStoreId() : null;
        if (storeId == null) {
            throw new IllegalStateException("Stop has no store: " + stop.getStopId());
        }

        StoreWarehouse storeWarehouse = storeWarehouseRepository.findByStore_StoreId(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found for store ID: " + storeId));

        for (Map.Entry<Long, BigDecimal> demandEntry : demandByProduct.entrySet()) {
            Long productId = demandEntry.getKey();
            BigDecimal remainingDemand = demandEntry.getValue();

            BigDecimal alreadyTransferred = alreadyTransferredByProduct.getOrDefault(productId, BigDecimal.ZERO);
            BigDecimal remainingPoolForProduct = recordsByProduct.getOrDefault(productId, List.of()).stream()
                    .map(ShipmentSourcingRecord::getQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .subtract(alreadyTransferred);

            if (remainingPoolForProduct.compareTo(remainingDemand) < 0) {
                throw new IllegalStateException("Insufficient sourced stock for product ID " + productId
                        + " at stop " + stop.getStopId() + ". Missing: " + remainingDemand.subtract(remainingPoolForProduct));
            }

            List<ShipmentSourcingRecord> productRecords = recordsByProduct.getOrDefault(productId, List.of());
            BigDecimal transferredFromProduct = alreadyTransferred;
            for (ShipmentSourcingRecord record : productRecords) {
                if (remainingDemand.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }

                BigDecimal recordQty = record.getQuantity();
                if (transferredFromProduct.compareTo(recordQty) >= 0) {
                    transferredFromProduct = transferredFromProduct.subtract(recordQty);
                    continue;
                }

                BigDecimal availableInRecord = recordQty.subtract(transferredFromProduct);
                transferredFromProduct = BigDecimal.ZERO;

                BigDecimal transferQty = availableInRecord.min(remainingDemand);
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
                        .note("Transfer for stop #" + stop.getStopId())
                        .createdAt(LocalDateTime.now())
                        .build();
                inventoryTransactionRepository.save(transaction);
            }
        }

        for (StoreOrder order : stopOrders) {
            if (order.getInvoice() != null) {
                Invoice invoice = order.getInvoice();
                invoice.setStatus(InvoiceStatus.FULFILLED);
                invoiceRepository.save(invoice);
            }
        }
    }

    private void releaseUndeliveredOrders(Shipment shipment, ShipmentStopStatus stopStatus) {
        List<ShipmentStop> stops = shipment.getStops() == null ? List.of() : shipment.getStops();
        for (ShipmentStop stop : stops) {
            if (stop.getStatus() == ShipmentStopStatus.DELIVERED) {
                continue;
            }
            List<StoreOrder> stopOrders = storeOrderRepository.findByShipmentStop_StopId(stop.getStopId());
            for (StoreOrder order : stopOrders) {
                order.setShipmentStop(null);
                order.setStatus(OrderStatus.READY);
            }
            storeOrderRepository.saveAll(stopOrders);
            stop.setStatus(stopStatus);
        }
    }

    private void restoreKitchenStockForUndelivered(Shipment shipment, String note) {
        if (shipment.getStops() == null || shipment.getStops().isEmpty()) {
            return;
        }

        Set<Long> deliveredStopIds = shipment.getStops().stream()
                .filter(stop -> stop.getStatus() == ShipmentStopStatus.DELIVERED)
                .map(ShipmentStop::getStopId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<StoreOrder> deliveredOrders = new ArrayList<>();
        for (Long stopId : deliveredStopIds) {
            deliveredOrders.addAll(storeOrderRepository.findByShipmentStop_StopId(stopId));
        }

        Map<Long, BigDecimal> deliveredDemandByProduct = new HashMap<>();
        for (StoreOrder order : deliveredOrders) {
            for (OrderDetail detail : order.getOrderDetails()) {
                deliveredDemandByProduct.merge(
                        detail.getProduct().getId(),
                        BigDecimal.valueOf(detail.getQuantity()),
                        BigDecimal::add
                );
            }
        }

        List<ShipmentSourcingRecord> records = shipmentSourcingRecordRepository.findByShipment_ShipmentId(shipment.getShipmentId());
        Map<Long, BigDecimal> sourcedByProduct = records.stream()
                .collect(Collectors.groupingBy(
                        record -> record.getProduct().getId(),
                        Collectors.mapping(ShipmentSourcingRecord::getQuantity,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))
                ));

        KitchenWarehouse kitchenWarehouse = kitchenWarehouseRepository.findById(1L)
                .orElseThrow(() -> new ResourceNotFoundException("Central Kitchen Warehouse not found"));

        for (Map.Entry<Long, BigDecimal> sourced : sourcedByProduct.entrySet()) {
            Long productId = sourced.getKey();
            BigDecimal undeliveredQty = sourced.getValue().subtract(
                    deliveredDemandByProduct.getOrDefault(productId, BigDecimal.ZERO)
            );

            if (undeliveredQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            List<KitchenStockItem> productStocks = kitchenStockItemRepository
                    .findByWarehouse_WarehouseIdAndProduct_Id(kitchenWarehouse.getWarehouseId(), productId);

            KitchenStockItem targetStock = productStocks.stream()
                    .findFirst()
                    .orElseGet(() -> {
                        ShipmentSourcingRecord referenceRecord = records.stream()
                                .filter(r -> r.getProduct().getId().equals(productId))
                                .findFirst()
                                .orElseThrow(() -> new IllegalStateException("Missing sourcing record for product " + productId));
                        KitchenStockItem newStock = KitchenStockItem.builder()
                                .warehouse(kitchenWarehouse)
                                .product(referenceRecord.getProduct())
                                .quantity(BigDecimal.ZERO)
                                .expiryDate(referenceRecord.getExpiryDate())
                                .productionPlan(referenceRecord.getProductionPlan())
                                .build();
                        return kitchenStockItemRepository.save(newStock);
                    });

            targetStock.setQuantity(targetStock.getQuantity().add(undeliveredQty));
            kitchenStockItemRepository.save(targetStock);

            ShipmentSourcingRecord referenceRecord = records.stream()
                    .filter(r -> r.getProduct().getId().equals(productId))
                    .findFirst()
                    .orElse(null);

            InventoryTransaction transaction = InventoryTransaction.builder()
                    .product(targetStock.getProduct())
                    .quantity(undeliveredQty)
                    .type(InventoryTransactionType.KITCHEN_ADJUST)
                    .refId(shipment.getShipmentId())
                    .kitchenWarehouse(kitchenWarehouse)
                    .expiryDate(targetStock.getExpiryDate())
                    .productionPlan(referenceRecord != null ? referenceRecord.getProductionPlan() : null)
                    .note(note)
                    .createdAt(LocalDateTime.now())
                    .build();
            inventoryTransactionRepository.save(transaction);
        }
    }

    private ShipmentStop getStopOrThrow(Shipment shipment, Long stopId) {
        if (shipment.getStops() == null) {
            throw new ResourceNotFoundException("Stop not found in shipment: " + stopId);
        }
        return shipment.getStops().stream()
                .filter(stop -> Objects.equals(stop.getStopId(), stopId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Stop not found in shipment: " + stopId));
    }

    private boolean isAllStopsDelivered(Shipment shipment) {
        return shipment.getStops() != null
                && !shipment.getStops().isEmpty()
                && shipment.getStops().stream().allMatch(stop -> stop.getStatus() == ShipmentStopStatus.DELIVERED);
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

    private boolean hasStoreAccessToStop(ShipmentStop stop, Long storeId) {
        return stop != null
                && stop.getStore() != null
                && storeId != null
                && Objects.equals(stop.getStore().getStoreId(), storeId);
    }
    private ShipmentStopStatus mapToStopStatus(ShipmentStatus status) {
        if (status == null) return null;
        return switch (status) {
            case ARRIVED -> ShipmentStopStatus.ARRIVED;
            case DELIVERED -> ShipmentStopStatus.DELIVERED;
            case IN_TRANSIT -> ShipmentStopStatus.IN_TRANSIT;
            case PENDING, PREPARED -> ShipmentStopStatus.PENDING;
            case CANCELLED, DELIVERY_FAILED -> ShipmentStopStatus.CANCELLED;
            case RETURNED -> ShipmentStopStatus.RETURNED;
            default -> null;
        };
    }
}