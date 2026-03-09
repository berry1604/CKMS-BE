package com.swp.ckms.service.impl;

import com.swp.ckms.dto.ahamove.AhamoveOrderRequest;
import com.swp.ckms.dto.ahamove.AhamoveOrderRequest.AhamoveItem;
import com.swp.ckms.dto.ahamove.AhamoveOrderRequest.AhamovePoint;
import com.swp.ckms.dto.ahamove.AhamoveOrderResponse;
import com.swp.ckms.entity.FranchiseStore;
import com.swp.ckms.entity.Shipment;
import com.swp.ckms.entity.StoreOrder;
import com.swp.ckms.enums.ShipmentStatus;
import com.swp.ckms.repository.ShipmentRepository;
import com.swp.ckms.repository.StoreOrderRepository;
import com.swp.ckms.service.AhamoveService;
import com.swp.ckms.service.AhamoveShipmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AhamoveShipmentServiceImpl implements AhamoveShipmentService {

    // Inject AhamoveService (interface) - không quan tâm impl là gì
    private final AhamoveService ahamoveService;
    private final ShipmentRepository shipmentRepository;
    private final StoreOrderRepository storeOrderRepository;

    private static final String KITCHEN_ADDRESS = "Số 1, Đường ABC, Quận 1, TP.HCM";
    private static final double KITCHEN_LAT     = 10.7769;
    private static final double KITCHEN_LNG     = 106.7009;
    private static final String KITCHEN_PHONE   = "84945751684";
    private static final String KITCHEN_NAME    = "CKMS - Central Kitchen";

    @Override
    @Transactional
    public void dispatchToAhamove(Shipment shipment) {
        log.info("Gửi shipment #{} lên Ahamove", shipment.getShipmentId());

        // B1: Lấy token xác thực
        String token = ahamoveService.getAccessToken();

        // B2: Lấy danh sách orders để build items
        List<StoreOrder> orders = storeOrderRepository
                .findByShipment_ShipmentId(shipment.getShipmentId());

        // B3: Build request từ dữ liệu Shipment
        AhamoveOrderRequest request = buildAhamoveRequest(shipment, orders);

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
        if (ahamoveStatus == null) return null;
        return switch (ahamoveStatus.toUpperCase()) {
            case "ASSIGNING",
                 "ACCEPTED",
                 "IN PROCESS" -> ShipmentStatus.IN_TRANSIT;
            case "COMPLETED"  -> ShipmentStatus.DELIVERED;
            case "CANCELLED",
                 "FAILED"     -> ShipmentStatus.CANCELLED;
            default -> {
                log.warn("Không nhận ra Ahamove status: {}", ahamoveStatus);
                yield null;
            }
        };
    }

    // ==================== Private Helpers ====================

    /**
     * Build AhamoveOrderRequest từ dữ liệu Shipment + StoreOrders.
     * Điểm pickup = bếp trung tâm, điểm dropoff = cửa hàng.
     */
    private AhamoveOrderRequest buildAhamoveRequest(
            Shipment shipment, List<StoreOrder> orders) {

        FranchiseStore store = shipment.getStore();

        return AhamoveOrderRequest.builder()
                .serviceId("SGN-BIKE")
                .paymentMethod("BALANCE")
                .remarks("Shipment #" + shipment.getShipmentId()
                        + " - " + store.getName())
                .path(List.of(
                        buildPickupPoint(shipment),
                        buildDropoffPoint(store)
                ))
                .items(buildItemList(orders))
                .build();
    }

    private AhamovePoint buildPickupPoint(Shipment shipment) {
        return AhamovePoint.builder()
                .address(KITCHEN_ADDRESS)
                .lat(KITCHEN_LAT)
                .lng(KITCHEN_LNG)
                .name(KITCHEN_NAME)
                .mobile(KITCHEN_PHONE)
                .remarks("Lấy hàng - Shipment #" + shipment.getShipmentId())
                .build();
    }

    private AhamovePoint buildDropoffPoint(FranchiseStore store) {
        return AhamovePoint.builder()
                .address(store.getAddress() != null
                        ? store.getAddress() : "Địa chỉ cửa hàng")
                .lat(store.getLatitude()  != null ? store.getLatitude()  : KITCHEN_LAT)
                .lng(store.getLongitude() != null ? store.getLongitude() : KITCHEN_LNG)
                .name(store.getName())
                .mobile(store.getPhone() != null ? store.getPhone() : "")
                .remarks("Giao hàng cho cửa hàng " + store.getName())
                .build();
    }

    private List<AhamoveItem> buildItemList(List<StoreOrder> orders) {
        return orders.stream()
                .flatMap(order -> order.getOrderDetails().stream())
                .map(detail -> AhamoveItem.builder()
                        .id(String.valueOf(detail.getProduct().getId()))
                        .name(detail.getProduct().getName())
                        .num(detail.getQuantity())
                        .price(detail.getProduct().getPrice() != null
                                ? detail.getProduct().getPrice().doubleValue()
                                : 0.0)
                        .build())
                .collect(Collectors.toList());
    }
}