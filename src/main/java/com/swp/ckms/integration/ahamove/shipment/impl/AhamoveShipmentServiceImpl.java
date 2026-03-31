package com.swp.ckms.integration.ahamove.shipment.impl;

import com.swp.ckms.entity.*;
import com.swp.ckms.enums.*;
import com.swp.ckms.repository.*;
import com.swp.ckms.integration.ahamove.config.AhamoveProperties;
import com.swp.ckms.integration.ahamove.dto.request.AhamoveOrderRequest;
import com.swp.ckms.integration.ahamove.dto.request.AhamoveOrderRequest.AhamoveItem;
import com.swp.ckms.integration.ahamove.dto.request.AhamoveOrderRequest.AhamovePoint;
import com.swp.ckms.integration.ahamove.dto.response.AhamoveOrderResponse;
import com.swp.ckms.integration.ahamove.service.AhamoveService;
import com.swp.ckms.integration.ahamove.shipment.AhamoveShipmentService;
import com.swp.ckms.integration.ahamove.dto.request.AhamoveWebhookRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AhamoveShipmentServiceImpl implements AhamoveShipmentService {

    // Inject AhamoveService (interface) - không quan tâm impl là gì
    private final AhamoveService ahamoveService;
    private final ShipmentRepository shipmentRepository;
    private final StoreOrderRepository storeOrderRepository;
    private final KitchenStockItemRepository kitchenStockItemRepository;
    private final KitchenWarehouseRepository kitchenWarehouseRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final ShipmentSourcingRecordRepository shipmentSourcingRecordRepository;
    private final AhamoveProperties ahamoveProperties;
    private final com.swp.ckms.service.NotificationService notificationService;
    private final com.swp.ckms.util.RecipientResolver recipientResolver;

    @Override
    @Transactional
    public void dispatchToAhamove(Shipment shipment) {
        log.info("Gửi shipment #{} lên Ahamove", shipment.getShipmentId());

        // B1: Lấy token xác thực
        String token = ahamoveService.getAccessToken();

        // B2: Lấy danh sách orders để build items

        // B3: Build request từ dữ liệu Shipment
        AhamoveOrderRequest request = buildAhamoveRequest(shipment);

        // B4: Gọi Ahamove tạo đơn
        AhamoveOrderResponse response = ahamoveService.createOrder(token, request);

        // B5: Lưu kết quả vào Shipment
        shipment.setAhamoveOrderId(response.getOrderId());
        shipment.setTrackingLink(response.getSharedLink());
        shipment.setAhamoveStatus(response.getStatus());
        if (response.getTotalPay() > 0) {
            shipment.setShippingFee(BigDecimal.valueOf(response.getTotalPay()));
        }
        shipmentRepository.save(shipment);

        log.info("Tạo đơn Ahamove thành công: orderId={} | tracking={}",
                response.getOrderId(), response.getSharedLink());
    }

    @Override
    public void cancelAhamoveOrder(Shipment shipment, String reason) {
        // Chưa tạo đơn Ahamove thì không cần hủy
        if (shipment.getAhamoveOrderId() == null) {
            log.info("Shipment #{} chưa có đơn Ahamove, bỏ qua bước hủy",
                    shipment.getShipmentId());
            return;
        }
        try {
            String token = ahamoveService.getAccessToken();
            ahamoveService.cancelOrder(token, shipment.getAhamoveOrderId(), reason);
            log.info("Đã hủy đơn Ahamove {} cho shipment #{}",
                    shipment.getAhamoveOrderId(), shipment.getShipmentId());
        } catch (Exception e) {
            // Không throw để cancel nội bộ vẫn tiếp tục
            log.warn("Không thể hủy đơn Ahamove cho shipment #{}: {}",
                    shipment.getShipmentId(), e.getMessage());
        }
    }

    @Override
    public ShipmentStatus mapAhamoveStatus(String ahamoveStatus) {
        String normalizedStatus = normalizeAhamoveStatus(ahamoveStatus);
        if (normalizedStatus == null) return null;
        return switch (normalizedStatus) {
            case "ASSIGNING" -> ShipmentStatus.PREPARED;
            case "PICKED UP", "ACCEPTED", "IN PROCESS" -> ShipmentStatus.IN_TRANSIT;
            case "COMPLETED"  -> ShipmentStatus.DELIVERED;
            case "RETURNING", "RETURNED" -> ShipmentStatus.RETURNED;
            case "CANCELED", "CANCELLED", "FAILED"     -> ShipmentStatus.CANCELLED;
            default -> {
                log.warn("Không nhận ra Ahamove status: {}", ahamoveStatus);
                yield null;
            }
        };
    }

    private OrderStatus mapAhamoveOrderStatus(String ahamoveStatus) {
        String normalizedStatus = normalizeAhamoveStatus(ahamoveStatus);
        if (normalizedStatus == null) return null;
        return switch (normalizedStatus) {
            case "ASSIGNING" -> OrderStatus.READY;
            case "PICKED UP", "ACCEPTED", "IN PROCESS" -> OrderStatus.IN_TRANSIT;
            case "COMPLETED"  -> OrderStatus.DELIVERED;
            case "RETURNING", "RETURNED" -> OrderStatus.RETURNED;
            case "CANCELED", "CANCELLED" -> OrderStatus.CANCELLED;
            case "FAILED" -> OrderStatus.DELIVERY_FAILED;
            default -> {
                log.warn("Không map được Ahamove status cho order: {}", ahamoveStatus);
                yield null;
            }
        };
    }

    private String normalizeAhamoveStatus(String ahamoveStatus) {
        if (ahamoveStatus == null) {
            return null;
        }
        return ahamoveStatus.trim().toUpperCase().replace('_', ' ');
    }

    @Override
    @Transactional
    public boolean handleWebhookUpdate(AhamoveWebhookRequest request) {
        Optional<Shipment> optShipment = shipmentRepository
                .findByAhamoveOrderId(request.getOrderId());
        if (optShipment.isEmpty()) {
            log.warn("Không tìm thấy shipment nào với Ahamove orderId: {}",
                    request.getOrderId());
            return false;
        }
        Shipment shipment = optShipment.get();
        ShipmentStatus oldStatus = shipment.getStatus();
        shipment.setAhamoveStatus(request.getStatus());
        if(request.getSupplierName() != null && !request.getSupplierName().isEmpty()) {
            shipment.setDriverName(request.getSupplierName());
        }
        if(request.getSupplierMobile() != null && !request.getSupplierMobile().isEmpty()) {
            shipment.setDriverPhone(request.getSupplierMobile());
        }

        ShipmentStatus newStatus = mapAhamoveStatus(request.getStatus());
        log.info("Nhận Webhook từ Ahamove: orderId={} | status={} | mappedStatus={}", 
                request.getOrderId(), request.getStatus(), newStatus);

        if ("ACCEPTED".equalsIgnoreCase(request.getStatus()) || "IN PROCESS".equalsIgnoreCase(request.getStatus())) {
            log.info("Tiến hành gửi thông báo lộ trình cho Shipment #{} (Status: {})", shipment.getShipmentId(), request.getStatus());
            // --- TRIGGER NOTIFICATION (Driver has accepted, info is available) ---
            try {
                java.util.Set<Long> notifiedStoreIds = new java.util.HashSet<>();
                for (ShipmentStop stop : shipment.getStops()) {
                    if (stop.getStore() == null || !notifiedStoreIds.add(stop.getStore().getStoreId())) {
                        continue;
                    }

                    String recipient = recipientResolver.resolveStoreManagerEmail(stop.getStore().getStoreId(),
                            shipment.getCreatedBy() != null ? shipment.getCreatedBy().getUserId() : null);
                    
                    if (recipient == null) {
                        log.warn("Không tìm thấy email Quản lý cho Store #{} của Shipment #{}", 
                                stop.getStore().getStoreId(), shipment.getShipmentId());
                        continue;
                    }

                    java.util.Map<String, Object> payload = new java.util.HashMap<>();
                    payload.put("shipmentId", shipment.getShipmentId());
                    payload.put("storeName", stop.getStore().getName());
                    payload.put("driverName", shipment.getDriverName() != null ? shipment.getDriverName() : "AhaMove Driver");
                    payload.put("driverPhone", shipment.getDriverPhone() != null ? shipment.getDriverPhone() : "N/A");
                    payload.put("vehicleInfo", shipment.getVehicleInfo() != null ? shipment.getVehicleInfo() : "Theo dõi qua Ahamove");
                    payload.put("trackingLink", "http://localhost:5173/shipments/" + shipment.getShipmentId());

                    notificationService.createEmailNotification(
                            com.swp.ckms.enums.NotificationType.SHIPMENT_STARTED,
                            recipient,
                            "shipment-started.html",
                            payload,
                            "SHIPMENT_STARTED_" + shipment.getShipmentId() + "_STORE_" + stop.getStore().getStoreId()
                    );
                    log.info("Đã tạo email thông báo lộ trình cho Store: {} (Recipient: {})", stop.getStore().getName(), recipient);
                }
            } catch (Exception e) {
                log.error("Failed to trigger shipment started notification via Ahamove Webhook", e);
            }
        }

        if (shipment.getStops() != null && !shipment.getStops().isEmpty()) {
            reconcileStopsFromWebhookPath(shipment, request);
        }

        if (isAllStopsArrivedOrDelivered(shipment)) {
            if (shipment.getStatus() != ShipmentStatus.DELIVERED) {
                shipment.setStatus(ShipmentStatus.ARRIVED);
            }
            if (shipment.getDeliveredAt() == null) {
                shipment.setDeliveredAt(LocalDateTime.now());
            }
        } else if (newStatus == ShipmentStatus.CANCELLED || newStatus == ShipmentStatus.RETURNED) {
            releaseUndeliveredOrders(shipment,
                    newStatus == ShipmentStatus.RETURNED ? ShipmentStopStatus.RETURNED : ShipmentStopStatus.CANCELLED,
                    newStatus == ShipmentStatus.RETURNED ? OrderStatus.RETURNED : OrderStatus.CANCELLED);
            restoreKitchenStockForUndelivered(shipment, "Webhook " + newStatus + " from Ahamove");
            shipment.setStatus(newStatus);
            shipment.setCancelledAt(LocalDateTime.now());
        } else if (newStatus != null && oldStatus != ShipmentStatus.DELIVERED) {
            shipment.setStatus(newStatus);
            if (newStatus == ShipmentStatus.CANCELLED) {
                shipment.setCancelledAt(LocalDateTime.now());
            }
        }

        if (request.getPath() == null || request.getPath().isEmpty()) {
            // Fallback for webhook payload without per-stop path information.
            OrderStatus fallbackOrderStatus = mapAhamoveOrderStatus(request.getStatus());
            if (fallbackOrderStatus != null && shipment.getStops() != null && shipment.getStops().size() == 1) {
                ShipmentStop onlyStop = shipment.getStops().get(0);
                if (fallbackOrderStatus == OrderStatus.DELIVERED) {
                    markStopArrived(onlyStop, null);
                }
            }
        }

        shipmentRepository.save(shipment);
        return true;
    }

    private void reconcileStopsFromWebhookPath(Shipment shipment, AhamoveWebhookRequest request) {
        List<ShipmentStop> stops = shipment.getStops().stream()
                .sorted(Comparator.comparing(ShipmentStop::getStopOrder))
                .toList();

        List<AhamoveWebhookRequest.AhamovePathPoint> pathPoints = request.getPath();
        if (pathPoints == null || pathPoints.isEmpty()) {
            return;
        }

        for (ShipmentStop stop : stops) {
            int pathIndex = stop.getStopOrder() == null ? -1 : stop.getStopOrder();
            if (pathIndex < 1 || pathIndex >= pathPoints.size()) {
                continue;
            }

            String stopRawStatus = pathPoints.get(pathIndex).getStatus();
            OrderStatus orderStatus = mapAhamoveOrderStatus(stopRawStatus);
            if (orderStatus == null) {
                continue;
            }

            if (orderStatus == OrderStatus.DELIVERED) {
                markStopArrived(stop, request);
                continue;
            }

            if (stop.getStatus() == ShipmentStopStatus.DELIVERED) {
                continue;
            }

            ShipmentStopStatus stopStatus = toStopStatus(orderStatus);
            stop.setStatus(stopStatus);

            List<StoreOrder> stopOrders = storeOrderRepository.findByShipmentStop_StopId(stop.getStopId());
            for (StoreOrder order : stopOrders) {
                if (orderStatus == OrderStatus.CANCELLED
                        || orderStatus == OrderStatus.RETURNED
                        || orderStatus == OrderStatus.DELIVERY_FAILED) {
                    order.setShipmentStop(null);
                    order.setStatus(OrderStatus.READY);
                } else {
                    order.setStatus(orderStatus);
                }
            }
            storeOrderRepository.saveAll(stopOrders);
        }
    }

    private void markStopArrived(ShipmentStop stop, AhamoveWebhookRequest request) {
        if (stop.getStatus() == ShipmentStopStatus.DELIVERED || stop.getStatus() == ShipmentStopStatus.ARRIVED) {
            return;
        }
        stop.setStatus(ShipmentStopStatus.ARRIVED);
        stop.setDeliveredAt(LocalDateTime.now());
        if (request != null && request.getComment() != null && !request.getComment().isBlank()) {
            stop.setRemarks(stop.getRemarks() != null
                    ? stop.getRemarks() + " | Webhook(Arrived): " + request.getComment()
                    : "Webhook(Arrived): " + request.getComment());
        }
    }

    private boolean isAllStopsArrivedOrDelivered(Shipment shipment) {
        return shipment.getStops().stream()
                .allMatch(s -> s.getStatus() == ShipmentStopStatus.ARRIVED || s.getStatus() == ShipmentStopStatus.DELIVERED);
    }

    private ShipmentStopStatus toStopStatus(OrderStatus orderStatus) {
        if (orderStatus == null) {
            return ShipmentStopStatus.PENDING;
        }
        return switch (orderStatus) {
            case DELIVERED -> ShipmentStopStatus.DELIVERED;
            case RETURNED -> ShipmentStopStatus.RETURNED;
            case CANCELLED, DELIVERY_FAILED -> ShipmentStopStatus.CANCELLED;
            case IN_TRANSIT -> ShipmentStopStatus.IN_TRANSIT;
            default -> ShipmentStopStatus.PENDING;
        };
    }

    private boolean isAllStopsDelivered(Shipment shipment) {
        return shipment.getStops() != null
                && !shipment.getStops().isEmpty()
                && shipment.getStops().stream().allMatch(stop -> stop.getStatus() == ShipmentStopStatus.DELIVERED);
    }

    private void releaseUndeliveredOrders(
            Shipment shipment,
            ShipmentStopStatus stopStatus,
            OrderStatus finalStopOrderStatus
    ) {
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
            if (finalStopOrderStatus == OrderStatus.RETURNED) {
                stop.setRemarks(stop.getRemarks() != null
                        ? stop.getRemarks() + " | Returned by carrier"
                        : "Returned by carrier");
            }
        }
    }

    private void restoreKitchenStockForUndelivered(Shipment shipment, String note) {
        if (shipment.getStops() == null || shipment.getStops().isEmpty()) {
            return;
        }

        List<StoreOrder> deliveredOrders = new ArrayList<>();
        for (ShipmentStop stop : shipment.getStops()) {
            if (stop.getStatus() == ShipmentStopStatus.DELIVERED) {
                deliveredOrders.addAll(storeOrderRepository.findByShipmentStop_StopId(stop.getStopId()));
            }
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
        if (records.isEmpty()) {
            return;
        }

        Map<Long, BigDecimal> sourcedByProduct = records.stream()
                .collect(Collectors.groupingBy(
                        record -> record.getProduct().getId(),
                        Collectors.mapping(ShipmentSourcingRecord::getQuantity,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))
                ));

        KitchenWarehouse kitchenWarehouse = kitchenWarehouseRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Central Kitchen Warehouse not found"));

        for (Map.Entry<Long, BigDecimal> sourced : sourcedByProduct.entrySet()) {
            Long productId = sourced.getKey();
            BigDecimal undeliveredQty = sourced.getValue().subtract(
                    deliveredDemandByProduct.getOrDefault(productId, BigDecimal.ZERO)
            );

            if (undeliveredQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            List<KitchenStockItem> stocks = kitchenStockItemRepository
                    .findByWarehouse_WarehouseIdAndProduct_Id(kitchenWarehouse.getWarehouseId(), productId);
            KitchenStockItem target = stocks.stream().findFirst().orElseGet(() -> {
                ShipmentSourcingRecord recordRef = records.stream()
                        .filter(r -> Objects.equals(r.getProduct().getId(), productId))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No sourcing record for product " + productId));
                KitchenStockItem created = KitchenStockItem.builder()
                        .warehouse(kitchenWarehouse)
                        .product(recordRef.getProduct())
                        .quantity(BigDecimal.ZERO)
                        .expiryDate(recordRef.getExpiryDate())
                        .productionPlan(recordRef.getProductionPlan())
                        .build();
                return kitchenStockItemRepository.save(created);
            });

            target.setQuantity(target.getQuantity().add(undeliveredQty));
            kitchenStockItemRepository.save(target);

            InventoryTransaction tx = InventoryTransaction.builder()
                    .product(target.getProduct())
                    .quantity(undeliveredQty)
                    .type(InventoryTransactionType.KITCHEN_ADJUST)
                    .refId(shipment.getShipmentId())
                    .kitchenWarehouse(kitchenWarehouse)
                    .expiryDate(target.getExpiryDate())
                    .note(note)
                    .createdAt(LocalDateTime.now())
                    .build();
            inventoryTransactionRepository.save(tx);
        }
    }

    // ==================== Private Helpers ====================

    /**
     * Build AhamoveOrderRequest từ dữ liệu Shipment + StoreOrders.
     * Điểm pickup = bếp trung tâm, điểm dropoff = cửa hàng.
     */
    // private AhamoveOrderRequest buildAhamoveRequest(
    //         Shipment shipment, List<StoreOrder> orders, String token) {


    private AhamoveOrderRequest buildAhamoveRequest(Shipment shipment) {
    CentralKitchen kitchen = shipment.getProductionPlan().getKitchen();

    // Build path: [0] = pickup (bếp), [1..n] = dropoff (các cửa hàng)
    List<AhamovePoint> path = new ArrayList<>();
    
    // Điểm lấy hàng (bếp trung tâm)
    path.add(AhamovePoint.builder()
            .address(kitchen.getAddress())
            .lat(kitchen.getLatitude())
            .lng(kitchen.getLongitude())
            .name(kitchen.getName())
            .mobile(kitchen.getPhone())
            .remarks("Lấy hàng - Shipment #" + shipment.getShipmentId())
            .build());

    // Các điểm giao hàng (multi-drop)
    List<AhamoveItem> allItems = new ArrayList<>();
    
    for (ShipmentStop stop : shipment.getStops()) {
        FranchiseStore store = stop.getStore();
        
        path.add(AhamovePoint.builder()
                .address(store.getAddress())
            .lat(store.getLatitude() != null ? store.getLatitude() : 0.0)
            .lng(store.getLongitude() != null ? store.getLongitude() : 0.0)
                .name(store.getName())
                .mobile(store.getPhoneNumber() != null ? store.getPhoneNumber() : "")
                .remarks(stop.getRemarks() != null ? stop.getRemarks() : "Giao hàng cho " + store.getName())
                .trackingNumber("STOP-" + stop.getStopId()) // Bây giờ đã có thể sử dụng sau khi update DTO
                .build());

        if (store.getLatitude() == null || store.getLongitude() == null) {
            log.error("Cửa hàng ID {} ({}) thiếu tọa độ Lat/Lng! AhaMove có thể định vị sai.", store.getStoreId(), store.getName());
        }

        // Build items cho điểm này
        for (StoreOrder order : stop.getStoreOrders()) {
            for (OrderDetail detail : order.getOrderDetails()) {
                allItems.add(AhamoveItem.builder()
                        .id(String.valueOf(detail.getProduct().getId()))
                        .name(detail.getProduct().getName())
                        .num(detail.getQuantity())
                        .price(detail.getProduct().getPrice() != null 
                                ? detail.getProduct().getPrice().intValue() : 0)
                        .build());
            }
        }
    }

    return AhamoveOrderRequest.builder()
            .serviceId(shipment.getAhamoveServiceId())
            .orderTime(0)
            .paymentMethod("BALANCE")
            .path(path)
            .items(allItems)
            .remarks(shipment.getRemarks() != null ? shipment.getRemarks() 
                    : "Shipment #" + shipment.getShipmentId())
            .callbackUrl(ahamoveProperties.getCallbackUrl())
            .build();
}
}
