package com.swp.ckms.entity;

import com.swp.ckms.enums.InventoryTransactionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Thực thể lưu vết biến động kho (Audit Ledger).
 */
@Entity
@Table(name = "inventory_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kitchen_warehouse_id")
    private KitchenWarehouse kitchenWarehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_warehouse_id")
    private StoreWarehouse storeWarehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id")
    private Material material;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InventoryTransactionType type;

    @Column(name = "ref_id")
    private Long refId; // ID của đối tượng tham chiếu (ProductionPlanId, ShipmentId, v.v.)

    private LocalDate expiryDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "production_plan_id")
    private ProductionPlan productionPlan;

    @Column(name = "ref_line_id")
    private Long refLineId; // ID của dòng Requirement (nếu cần trace sâu)

    @Column(precision = 19, scale = 4)
    private BigDecimal unitCost; // Giá vốn tại thời điểm thao tác

    private String note;

    private LocalDateTime createdAt;

    /**
     * Tự động gán thời gian tạo nếu chưa có.
     */
    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
