package com.swp.ckms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "central_kitchens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CentralKitchen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long kitchenId;

    @Column(nullable = false)
    private String name;

    private String address;

    @Column(precision = 19, scale = 2)
    private java.math.BigDecimal maxDailyCapacity;
}
