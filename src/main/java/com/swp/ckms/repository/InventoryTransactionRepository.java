package com.swp.ckms.repository;

import com.swp.ckms.entity.InventoryTransaction;
import com.swp.ckms.enums.InventoryTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {
    
    /**
     * Tìm các transaction liên quan đến một mẻ sản xuất.
     */
    List<InventoryTransaction> findByRefIdAndType(Long refId, InventoryTransactionType type);

    /**
     * Kiểm tra xem một mẻ đã được hoàn kho chưa (Double Return Protection).
     */
    boolean existsByTypeAndRefId(InventoryTransactionType type, Long refId);
}
