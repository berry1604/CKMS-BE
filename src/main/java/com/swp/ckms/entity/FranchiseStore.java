package com.swp.ckms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "franchise_stores")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FranchiseStore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long storeId;

    @Column(nullable = false)
    private String name;

    private String address;

    private Double latitude;
    private Double longitude;

    private String paymentCycle;// e.g., "MONTHLY"
    
    private String phoneNumber;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;
}
