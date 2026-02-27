package com.swp.ckms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.swp.ckms.enums.ProductionPlanStatus;
import java.time.LocalDateTime;

@Entity
@Table(name = "production_plans")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long planId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coordinator_user_id")
    private User coordinatorUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kitchen_id")
    private CentralKitchen kitchen;

    private String planName;

    private String batchCode;

    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductionPlanStatus status;

    @Version
    private Long version;
}
