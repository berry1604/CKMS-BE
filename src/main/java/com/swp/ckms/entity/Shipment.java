package com.swp.ckms.entity;

import com.swp.ckms.enums.ShipmentStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "shipments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long shipmentId;
// Tích hợp ahamove thì thêm vào
    // @Column(name = "ahamove_order_id")
    // private String ahamoveOrderId;

    // @Column(name = "tracking_link")
    // private String trackingLink;

    // Shipment giao cho cửa hàng nào
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private FranchiseStore store;

    // Shipment thuộc production plan nào
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private ProductionPlan productionPlan;

    // Các đơn hàng trong shipment này
    @OneToMany(mappedBy = "shipment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<StoreOrder> storeOrders;

    // Thông tin tài xế (nhập tay)
    private String driverName;
    private String driverPhone;
    private String vehicleInfo;          // Biển số xe, loại xe

    private BigDecimal shippingFee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentStatus status;

    private String note;                  // Ghi chú của coordinator

    // Ai tạo shipment
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    // Store staff xác nhận nhận hàng
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by")
    private User confirmedBy;

    private LocalDateTime createdAt;
    private LocalDateTime shippedAt;      // Thời điểm xuất kho
    private LocalDateTime deliveredAt;    // Thời điểm store xác nhận nhận
    private LocalDateTime cancelledAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}