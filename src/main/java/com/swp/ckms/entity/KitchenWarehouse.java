package com.swp.ckms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "kitchen_warehouses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KitchenWarehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long warehouseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kitchen_id", nullable = false)
    private CentralKitchen kitchen;

    @Column(nullable = false)
    private String name;

    @Column(precision = 19, scale = 2)
    private java.math.BigDecimal maxCapacity;
}
