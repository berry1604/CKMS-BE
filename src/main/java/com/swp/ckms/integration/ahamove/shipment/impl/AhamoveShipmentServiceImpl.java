package com.swp.ckms.integration.ahamove.shipment.impl;

import com.swp.ckms.integration.ahamove.dto.request.AhamoveOrderRequest;
import com.swp.ckms.integration.ahamove.dto.request.AhamoveOrderRequest.AhamoveItem;
import com.swp.ckms.integration.ahamove.dto.request.AhamoveOrderRequest.AhamovePoint;
import com.swp.ckms.integration.ahamove.dto.response.AhamoveOrderResponse;
import com.swp.ckms.integration.ahamove.service.AhamoveService;
import com.swp.ckms.integration.ahamove.shipment.AhamoveShipmentService;
import com.swp.ckms.integration.ahamove.dto.request.AhamoveWebhookRequest;
import com.swp.ckms.entity.FranchiseStore;
import com.swp.ckms.entity.CentralKitchen;
import com.swp.ckms.entity.OrderDetail;
import com.swp.ckms.entity.ShipmentStop;
import com.swp.ckms.repository.CentralKitchenRepository;
import com.swp.ckms.entity.Shipment;
import com.swp.ckms.entity.StoreOrder;
import com.swp.ckms.enums.OrderStatus;
import com.swp.ckms.enums.ShipmentStatus;
import com.swp.ckms.repository.ShipmentRepository;
import com.swp.ckms.repository.StoreOrderRepository;
import com.swp.ckms.service.impl.ShipmentFeeAllocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.ArrayList;


import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AhamoveShipmentServiceImpl implements AhamoveShipmentService {

    // Inject AhamoveService (interface) - không quan tâm impl là gì
    private final AhamoveService ahamoveService;
    private final ShipmentRepository shipmentRepository;
    private final StoreOrderRepository storeOrderRepository;
    private final CentralKitchenRepository centralKitchenRepository;
    private final ShipmentFeeAllocationService shipmentFeeAllocationService;

    @Override
    @Transactional
    public void dispatchToAhamove(Shipment shipment) {
        log.info("Gửi shipment #{} lên Ahamove", shipment.getShipmentId());

        // B1: Lấy token xác thực
        String token = ahamoveService.getAccessToken();

        // B2: Lấy danh sách orders để build items
        List<StoreOrder> orders = storeOrderRepository
                .findByShipmentStop_Shipment_ShipmentId(shipment.getShipmentId());

        // B3: Build request từ dữ liệu Shipment
        // AhamoveOrderRequest request = buildAhamoveRequest(shipment, orders, token);
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
            case "CANCELED", "CANCELLED", "FAILED"     -> OrderStatus.CANCELLED;
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
        shipment.setAhamoveStatus(request.getStatus());
        if(request.getSupplierName() != null && !request.getSupplierName().isEmpty()) {
            shipment.setDriverName(request.getSupplierName());
        }
        if(request.getSupplierMobile() != null && !request.getSupplierMobile().isEmpty()) {
            shipment.setDriverPhone(request.getSupplierMobile());
        }
        ShipmentStatus newStatus = mapAhamoveStatus(request.getStatus());
        OrderStatus newOrderStatus = mapAhamoveOrderStatus(request.getStatus());

        if (newStatus != null && newStatus != shipment.getStatus()) {
                log.info("Shipment #{} cập nhật status: {} -> {}",
                        shipment.getShipmentId(), shipment.getStatus(), newStatus);
                shipment.setStatus(newStatus);

                if (newStatus == ShipmentStatus.DELIVERED) {
                shipment.setDeliveredAt(LocalDateTime.now());
                shipmentFeeAllocationService.allocateForDeliveredShipment(shipment);
                } else if (newStatus == ShipmentStatus.CANCELLED) {
                shipment.setCancelledAt(LocalDateTime.now());
                }
        }

        if (newOrderStatus != null) {
            List<StoreOrder> orders = storeOrderRepository
                    .findByShipmentStop_Shipment_ShipmentId(shipment.getShipmentId());

            int changedCount = 0;
            for (StoreOrder order : orders) {
                if (order.getStatus() != newOrderStatus) {
                    order.setStatus(newOrderStatus);
                    changedCount++;
                }
            }

            if (changedCount > 0) {
                storeOrderRepository.saveAll(orders);
                log.info("Shipment #{} reconcile {} order(s) -> {} từ webhook status '{}'",
                        shipment.getShipmentId(), changedCount, newOrderStatus, request.getStatus());
            }
        }

        shipmentRepository.save(shipment);
        return true;
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
                .lat(store.getLatitude() != null ? Double.parseDouble(store.getLatitude()) : kitchen.getLatitude())
                .lng(store.getLongitude() != null ? Double.parseDouble(store.getLongitude()) : kitchen.getLongitude())
                .name(store.getName())
                .mobile(store.getPhoneNumber() != null ? store.getPhoneNumber() : "")
                .remarks(stop.getRemarks() != null ? stop.getRemarks() : "Giao hàng cho " + store.getName())
                // .trackingNumber("STOP-" + stop.getStopId()) // Để tracking từng điểm
                .build());

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
            .callbackUrl("https://deprecatorily-nonmucilaginous-many.ngrok-free.dev/api/v1/webhooks/ahamove")
            .build();
}
}