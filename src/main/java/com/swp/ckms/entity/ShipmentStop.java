// ShipmentStop.java
package com.swp.ckms.entity;

import com.swp.ckms.enums.ShipmentStopStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "shipment_stops")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long stopId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private FranchiseStore store;

    @Column(nullable = false)
    private Integer stopOrder; // Thứ tự điểm dừng (1, 2, 3...)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ShipmentStopStatus status = ShipmentStopStatus.PENDING;

    private LocalDateTime deliveredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by")
    private User confirmedBy;

    // Ghi chú riêng cho điểm này
    private String remarks;

    // COD nếu có (AhaMove hỗ trợ)
    private java.math.BigDecimal codAmount;

    // Các đơn hàng giao tại điểm này
    @OneToMany(mappedBy = "shipmentStop", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<StoreOrder> storeOrders;
}