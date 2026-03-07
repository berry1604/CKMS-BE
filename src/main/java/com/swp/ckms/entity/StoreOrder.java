package com.swp.ckms.entity;

import com.swp.ckms.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "store_orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private FranchiseStore store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_plan_id")
    private ProductionPlan productionPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id")
    private Shipment shipment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    private LocalDateTime orderDate;

    private java.time.LocalDate deliveryDate;

    private BigDecimal orderFee;

    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status; // SUBMITTED, REJECTED, GROUPED, CONFIRMED, PREPARING, READY, COMPLETED

    private Long batchId; // used to group orders by coordinator

    private LocalDateTime approvedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderDetail> orderDetails;
}
