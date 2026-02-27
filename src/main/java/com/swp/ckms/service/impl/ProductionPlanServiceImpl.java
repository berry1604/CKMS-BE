package com.swp.ckms.service.impl;

import com.swp.ckms.dto.request.ProductionPlanRequest;
import com.swp.ckms.dto.response.ProductionPlanResponse;
import com.swp.ckms.entity.CentralKitchen;
import com.swp.ckms.entity.ProductionPlan;
import com.swp.ckms.entity.ProductionPlanMaterialRequirement;
import com.swp.ckms.entity.User;
import com.swp.ckms.enums.ProductionPlanStatus;
import com.swp.ckms.exception.business.OrderAssignmentConflictException;
import com.swp.ckms.repository.ProductionPlanMaterialRequirementRepository;
import com.swp.ckms.repository.ProductionPlanRepository;
import com.swp.ckms.repository.StoreOrderRepository;
import com.swp.ckms.repository.UserRepository;
import com.swp.ckms.repository.projection.MaterialRequirementProjection;
import com.swp.ckms.security.SecurityUtils;
import com.swp.ckms.security.UserContext;
import com.swp.ckms.service.ProductionPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProductionPlanServiceImpl implements ProductionPlanService {

    private final ProductionPlanRepository productionPlanRepository;
    private final StoreOrderRepository storeOrderRepository;
    private final UserRepository userRepository;
    private final ProductionPlanMaterialRequirementRepository materialRequirementRepository;
    private final com.swp.ckms.repository.KitchenWarehouseRepository warehouseRepository;
    private final com.swp.ckms.repository.KitchenStockItemRepository kitchenStockItemRepository;
    private final com.swp.ckms.repository.InventoryTransactionRepository inventoryTransactionRepository;

    @Override
    public ProductionPlanResponse createProductionPlan(ProductionPlanRequest request) {
        // Step 1: Get Current User & Kitchen
        UserContext ctx = SecurityUtils.getCurrentUserContext();
        if (ctx == null) {
            throw new AccessDeniedException("User context not found or not authenticated");
        }

        User currentUser = userRepository.findById(ctx.getUserId())
                .orElseThrow(() -> new AccessDeniedException("User not found in system"));

        CentralKitchen kitchen = currentUser.getKitchen();
        if (kitchen == null) {
            throw new AccessDeniedException("User does not belong to any Central Kitchen and cannot create a Production Plan.");
        }

        // Generate Plan Name
        String planName = "Plan_" + LocalDateTime.now().toString().replace(":", "-");

        // Step 2: Create ProductionPlan (NOT committed yet)
        ProductionPlan plan = ProductionPlan.builder()
                .kitchen(kitchen)
                .coordinatorUser(currentUser)
                .planName(planName)
                .status(ProductionPlanStatus.PLANNED)
                .createdAt(LocalDateTime.now())
                .build();

        ProductionPlan savedPlan = productionPlanRepository.save(plan);

        // Step 3: Atomic Assign Orders
        List<Long> orderIds = request.getStoreOrderIds();
        int updatedRows = storeOrderRepository.assignOrdersToPlan(savedPlan.getPlanId(), orderIds);

        // Row Count Validation
        if (updatedRows != orderIds.size()) {
            throw new OrderAssignmentConflictException("Một hoặc nhiều Order đã được xử lý bởi người khác hoặc không ở trạng thái CONFIRMED. Vui lòng reload!");
        }

        // Step 4: Snapshot Material Requirement
        List<MaterialRequirementProjection> requirements = storeOrderRepository.getMaterialRequirementsForPlan(savedPlan.getPlanId());

        if (requirements.isEmpty()) {
            log.warn("No material requirements found for Plan ID {}", savedPlan.getPlanId());
        }

        // Step 5: Save Snapshots
        for (MaterialRequirementProjection requirement : requirements) {
            ProductionPlanMaterialRequirement snapshot = ProductionPlanMaterialRequirement.builder()
                    .plan(savedPlan)
                    .material(requirement.getMaterial())
                    .requiredQuantity(requirement.getTotal())
                    .build();
            materialRequirementRepository.save(snapshot);
        }

        return ProductionPlanResponse.builder()
                .planId(savedPlan.getPlanId())
                .planName(savedPlan.getPlanName())
                .batchCode(savedPlan.getBatchCode())
                .kitchenId(kitchen.getKitchenId())
                .status(savedPlan.getStatus().name())
                .createdAt(savedPlan.getCreatedAt())
                .coordinatorUserId(currentUser.getUserId())
                .build();
    }

    @Override
    public ProductionPlanResponse checkAndReadyPlan(Long planId) {
        // Step 1: Fetch ProductionPlan
        ProductionPlan plan = productionPlanRepository.findById(planId)
                .orElseThrow(() -> new com.swp.ckms.exception.business.ResourceNotFoundException("Production Plan not found with id: " + planId));

        // Step 2: Validate status == PLANNED
        if (plan.getStatus() != ProductionPlanStatus.PLANNED) {
            throw new OrderAssignmentConflictException("Production Plan is not in PLANNED state. Current state: " + plan.getStatus());
        }

        // Validate Kitchen: User context current kitchen MUST match Plan's kitchen (or Admin)
        UserContext ctx = SecurityUtils.getCurrentUserContext();
        if (ctx == null) {
            throw new AccessDeniedException("User context not found or not authenticated");
        }
        User currentUser = userRepository.findById(ctx.getUserId())
                .orElseThrow(() -> new AccessDeniedException("User not found in system"));
        
        // Checking Management / Admin logic: either user is SYSTEM scope or User's kitchen == Plan's kitchen
        boolean isSystemScope = "SYSTEM".equalsIgnoreCase(ctx.getScope());
        if (!isSystemScope) {
            if (currentUser.getKitchen() == null || !currentUser.getKitchen().getKitchenId().equals(plan.getKitchen().getKitchenId())) {
                throw new AccessDeniedException("You do not have permission to modify this Production Plan.");
            }
        }

        // Step 4: Fetch default warehouse of the kitchen
        com.swp.ckms.entity.KitchenWarehouse warehouse = warehouseRepository.findByKitchen_KitchenId(plan.getKitchen().getKitchenId())
                .stream()
                .findFirst()
                .orElseThrow(() -> new com.swp.ckms.exception.business.ResourceNotFoundException("No default warehouse found for Kitchen ID: " + plan.getKitchen().getKitchenId()));

        // Step 5: Load List ProductionPlanMaterialRequirement (Snapshot)
        List<ProductionPlanMaterialRequirement> requirements = materialRequirementRepository.findByPlan_PlanId(planId);
        if (requirements.isEmpty()) {
            throw new OrderAssignmentConflictException("Plan has no material requirements. Cannot be ready to produce.");
        }

        // Step 6: Extract list of materialId
        List<Long> materialIds = requirements.stream()
                .map(req -> req.getMaterial().getId())
                .collect(java.util.stream.Collectors.toList());

        // Step 7: Lấy Map Stock
        List<com.swp.ckms.repository.projection.MaterialStockProjection> availableStocks = kitchenStockItemRepository.getAvailableStockForMaterials(warehouse.getWarehouseId(), materialIds);
        java.util.Map<Long, java.math.BigDecimal> stockMap = availableStocks.stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.swp.ckms.repository.projection.MaterialStockProjection::getMaterialId,
                        com.swp.ckms.repository.projection.MaterialStockProjection::getTotalQuantity,
                        java.math.BigDecimal::add // In case of duplicates, though logic shouldn't produce them
                ));

        // Step 8 & 9: Lặp và so sánh từng material
        List<com.swp.ckms.dto.response.MissingMaterialResponse> missingMaterials = new java.util.ArrayList<>();
        
        for (ProductionPlanMaterialRequirement req : requirements) {
            Long matId = req.getMaterial().getId();
            java.math.BigDecimal requiredQty = req.getRequiredQuantity();
            java.math.BigDecimal availableQty = stockMap.getOrDefault(matId, java.math.BigDecimal.ZERO);
            
            if (availableQty.compareTo(requiredQty) < 0) {
                missingMaterials.add(com.swp.ckms.dto.response.MissingMaterialResponse.builder()
                        .materialId(matId)
                        .materialName(req.getMaterial().getName())
                        .requiredQuantity(requiredQty)
                        .availableQuantity(availableQty)
                        .missingQuantity(requiredQty.subtract(availableQty))
                        .build());
            }
        }

        if (!missingMaterials.isEmpty()) {
            throw new com.swp.ckms.exception.business.InsufficientMaterialException("Not enough materials to ready the Production Plan.", missingMaterials);
        }

        // Step 10: Cập nhật plan.setStatus(READY_TO_PRODUCE)
        plan.setStatus(ProductionPlanStatus.READY_TO_PRODUCE);
        productionPlanRepository.save(plan);

        // Step 11: Return response
        return ProductionPlanResponse.builder()
                .planId(plan.getPlanId())
                .planName(plan.getPlanName())
                .batchCode(plan.getBatchCode())
                .kitchenId(plan.getKitchen().getKitchenId())
                .status(plan.getStatus().name())
                .createdAt(plan.getCreatedAt())
                .coordinatorUserId(plan.getCoordinatorUser().getUserId())
                .build();
    }

    @Override
    public ProductionPlanResponse startProductionPlan(Long planId, Long requestVersion) {
        // Step 1: Validate Kitchen: User context current kitchen MUST match Plan's kitchen (or Admin)
        UserContext ctx = SecurityUtils.getCurrentUserContext();
        if (ctx == null) {
            throw new AccessDeniedException("User context not found or not authenticated");
        }
        User currentUser = userRepository.findById(ctx.getUserId())
                .orElseThrow(() -> new AccessDeniedException("User not found in system"));

        boolean isSystemScope = "SYSTEM".equalsIgnoreCase(ctx.getScope());
        ProductionPlan plan;
        
        // Step 2: Fetch ProductionPlan
        if (!isSystemScope) {
            if (currentUser.getKitchen() == null) {
                throw new AccessDeniedException("You do not have permission to modify this Production Plan.");
            }
            plan = productionPlanRepository.findByPlanIdAndKitchen_KitchenId(planId, currentUser.getKitchen().getKitchenId())
                    .orElseThrow(() -> new com.swp.ckms.exception.business.ResourceNotFoundException("Production Plan not found or you don't have access. ID: " + planId));
        } else {
             plan = productionPlanRepository.findById(planId)
                    .orElseThrow(() -> new com.swp.ckms.exception.business.ResourceNotFoundException("Production Plan not found with id: " + planId));
        }

        // Step 3: Idempotency Check
        if (plan.getStatus() == ProductionPlanStatus.IN_PRODUCTION) {
             return ProductionPlanResponse.builder()
                .planId(plan.getPlanId())
                .planName(plan.getPlanName())
                .batchCode(plan.getBatchCode())
                .kitchenId(plan.getKitchen().getKitchenId())
                .status(plan.getStatus().name())
                .createdAt(plan.getCreatedAt())
                .coordinatorUserId(plan.getCoordinatorUser().getUserId())
                .build();
        }

        // Step 4: Validate status == READY_TO_PRODUCE
        if (plan.getStatus() != ProductionPlanStatus.READY_TO_PRODUCE) {
            throw new OrderAssignmentConflictException("Production Plan is not in READY_TO_PRODUCE state. Current state: " + plan.getStatus());
        }

        // Step 5: Optimistic Locking version set
        if (requestVersion != null) {
            plan.setVersion(requestVersion);
        }

        // Step 6: Fetch default warehouse of the kitchen
        com.swp.ckms.entity.KitchenWarehouse warehouse = warehouseRepository.findByKitchen_KitchenId(plan.getKitchen().getKitchenId())
                .stream()
                .findFirst()
                .orElseThrow(() -> new com.swp.ckms.exception.business.ResourceNotFoundException("No default warehouse found for Kitchen ID: " + plan.getKitchen().getKitchenId()));

        // Step 7: Load List ProductionPlanMaterialRequirement (Snapshot)
        List<ProductionPlanMaterialRequirement> requirements = materialRequirementRepository.findByPlan_PlanId(planId);
        if (requirements.isEmpty()) {
            throw new OrderAssignmentConflictException("Plan has no material requirements. Cannot start producing.");
        }

        List<Long> materialIds = requirements.stream()
                .map(req -> req.getMaterial().getId())
                .collect(java.util.stream.Collectors.toList());

        // Step 8: Lock Materials for Deduction (PESSIMISTIC_WRITE + ORDER BY materialId, expiryDate NULLS LAST, id)
        List<com.swp.ckms.entity.KitchenStockItem> lockedItems = kitchenStockItemRepository.lockMaterialsForDeduction(warehouse.getWarehouseId(), materialIds);

        // Group locked items by materialId for easier FIFO deduction
        java.util.Map<Long, java.util.List<com.swp.ckms.entity.KitchenStockItem>> itemsByMaterial = lockedItems.stream()
                .collect(java.util.stream.Collectors.groupingBy(item -> item.getMaterial().getId()));

        List<com.swp.ckms.dto.response.MissingMaterialResponse> missingMaterials = new java.util.ArrayList<>();

        // Step 9: FIFO Deduct Algorithm
        for (ProductionPlanMaterialRequirement req : requirements) {
            Long matId = req.getMaterial().getId();
            java.math.BigDecimal requiredQty = req.getRequiredQuantity();
            
            java.util.List<com.swp.ckms.entity.KitchenStockItem> availableItemsForMatId = itemsByMaterial.getOrDefault(matId, java.util.Collections.emptyList());
            
            for (com.swp.ckms.entity.KitchenStockItem item : availableItemsForMatId) {
                if (requiredQty.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                    break;
                }
                
                java.math.BigDecimal itemQty = item.getQuantity();
                if (itemQty.compareTo(java.math.BigDecimal.ZERO) <= 0) continue;
                
                java.math.BigDecimal deductedQty;
                if (itemQty.compareTo(requiredQty) <= 0) {
                    // Exhaust this item
                    deductedQty = itemQty;
                    requiredQty = requiredQty.subtract(itemQty);
                    item.setQuantity(java.math.BigDecimal.ZERO);
                } else {
                    // Partially use this item
                    deductedQty = requiredQty;
                    item.setQuantity(itemQty.subtract(requiredQty));
                    requiredQty = java.math.BigDecimal.ZERO;
                }

                // RECORD AUDIT TRANSACTION
                inventoryTransactionRepository.save(com.swp.ckms.entity.InventoryTransaction.builder()
                        .warehouse(warehouse)
                        .material(req.getMaterial())
                        .quantity(deductedQty.negate()) // Negative for deduction
                        .type(com.swp.ckms.enums.InventoryTransactionType.PRODUCTION_DEDUCT)
                        .refId(planId)
                        .refLineId(req.getId())
                        .createdAt(java.time.LocalDateTime.now())
                        .build());
            }
            
            // If requiredQty > 0 after checking all items, we are short on this material
            if (requiredQty.compareTo(java.math.BigDecimal.ZERO) > 0) {
                 java.math.BigDecimal availableQtyForMatId = availableItemsForMatId.stream()
                        .map(com.swp.ckms.entity.KitchenStockItem::getQuantity)
                        // Should technically sum up original qty before our deductions, 
                        // but req.getRequiredQuantity() - requiredQty is what we managed to deduct.
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
                        .add(req.getRequiredQuantity().subtract(requiredQty)); 

                 missingMaterials.add(com.swp.ckms.dto.response.MissingMaterialResponse.builder()
                        .materialId(matId)
                        .materialName(req.getMaterial().getName())
                        .requiredQuantity(req.getRequiredQuantity())
                        .availableQuantity(availableQtyForMatId)
                        .missingQuantity(requiredQty)
                        .build());
            }
        }

        if (!missingMaterials.isEmpty()) {
            throw new com.swp.ckms.exception.business.InsufficientMaterialException("Not enough materials to start the Production Plan.", missingMaterials);
        }

        // Step 11: Cập nhật plan.setStatus(IN_PRODUCTION)
        plan.setStatus(ProductionPlanStatus.IN_PRODUCTION);
        // Step 10 & 11: Save plan (triggers version check) and let dirty checking save KitchenStockItem
        productionPlanRepository.save(plan);

        // Step 12: Return response
        return ProductionPlanResponse.builder()
                .planId(plan.getPlanId())
                .planName(plan.getPlanName())
                .batchCode(plan.getBatchCode())
                .kitchenId(plan.getKitchen().getKitchenId())
                .status(plan.getStatus().name())
                .createdAt(plan.getCreatedAt())
                .coordinatorUserId(plan.getCoordinatorUser().getUserId())
                .build();
    }

    @Override
    public ProductionPlanResponse finishProductionPlan(Long planId, Long requestVersion) {
        // Step 1: Security & Identity validation
        UserContext ctx = SecurityUtils.getCurrentUserContext();
        if (ctx == null) {
            throw new AccessDeniedException("User context not found or not authenticated");
        }
        User currentUser = userRepository.findById(ctx.getUserId())
                .orElseThrow(() -> new AccessDeniedException("User not found in system"));

        boolean isSystemScope = "SYSTEM".equalsIgnoreCase(ctx.getScope());
        ProductionPlan plan;

        // Step 2: Fetch ProductionPlan with Kitchen Check
        if (!isSystemScope) {
            if (currentUser.getKitchen() == null) {
                throw new AccessDeniedException("You do not have permission to modify this Production Plan.");
            }
            plan = productionPlanRepository.findByPlanIdAndKitchen_KitchenId(planId, currentUser.getKitchen().getKitchenId())
                    .orElseThrow(() -> new com.swp.ckms.exception.business.ResourceNotFoundException("Production Plan not found or you don't have access. ID: " + planId));
        } else {
             plan = productionPlanRepository.findById(planId)
                    .orElseThrow(() -> new com.swp.ckms.exception.business.ResourceNotFoundException("Production Plan not found with id: " + planId));
        }

        // Step 3: Idempotency Check (CHECK FIRST)
        if (plan.getStatus() == ProductionPlanStatus.FINISHED) {
             return ProductionPlanResponse.builder()
                .planId(plan.getPlanId())
                .planName(plan.getPlanName())
                .batchCode(plan.getBatchCode())
                .kitchenId(plan.getKitchen().getKitchenId())
                .status(plan.getStatus().name())
                .createdAt(plan.getCreatedAt())
                .coordinatorUserId(plan.getCoordinatorUser().getUserId())
                .build();
        }

        // Step 4: State Guard
        if (plan.getStatus() != ProductionPlanStatus.IN_PRODUCTION) {
            throw new OrderAssignmentConflictException("Production Plan is not in IN_PRODUCTION state. Current state: " + plan.getStatus());
        }

        // Step 5: Transition Plan (Let Hibernate handle @Version)
        if (requestVersion != null) {
            // OPTIONAL: even though Hibernate checks on flush, setting it here helps fail-fast if necessary, 
            // but we'll stick to the "no manual override" rule if requestVersion is just a hint.
            // Actually, if we don't set it, we rely on the object in session's version.
            // If the user wants to enforce "Finish ONLY if you saw version X", we should set it.
            plan.setVersion(requestVersion);
        }
        
        plan.setStatus(ProductionPlanStatus.FINISHED);
        productionPlanRepository.save(plan);

        // Step 6: Bulk Update Store Orders (Only GROUPED -> READY)
        storeOrderRepository.updateOrderStatusToReadyByPlanId(planId);

        // Step 7: Feedback Loop - Add produced items to Kitchen Stock
        // Find default warehouse for the plan's kitchen
        com.swp.ckms.entity.KitchenWarehouse warehouse = warehouseRepository.findByKitchen_KitchenId(plan.getKitchen().getKitchenId())
                .stream()
                .findFirst()
                .orElseThrow(() -> new com.swp.ckms.exception.business.ResourceNotFoundException("No default warehouse found for production feedback loop. Kitchen ID: " + plan.getKitchen().getKitchenId()));

        // Aggregate products from all orders in this plan
        List<com.swp.ckms.entity.StoreOrder> orders = storeOrderRepository.findByProductionPlan_PlanId(planId);
        
        for (com.swp.ckms.entity.StoreOrder order : orders) {
            for (com.swp.ckms.entity.OrderDetail detail : order.getOrderDetails()) {
                com.swp.ckms.entity.Product product = detail.getProduct();
                java.math.BigDecimal producedQty = java.math.BigDecimal.valueOf(detail.getQuantity());

                // Find or create stock item (for finished product, batchCode might be plan's batchCode)
                com.swp.ckms.entity.KitchenStockItem stockItem = kitchenStockItemRepository
                        .findByWarehouse_WarehouseIdAndProduct_Id(warehouse.getWarehouseId(), product.getId())
                        .stream().findFirst()
                        .orElseGet(() -> com.swp.ckms.entity.KitchenStockItem.builder()
                                .warehouse(warehouse)
                                .product(product)
                                .quantity(java.math.BigDecimal.ZERO)
                                .build());

                stockItem.setQuantity(stockItem.getQuantity().add(producedQty));
                stockItem.setProductionPlan(plan); // Tag with the plan that produced it
                kitchenStockItemRepository.save(stockItem);

                // Audit: Record Transaction
                com.swp.ckms.entity.InventoryTransaction tx = com.swp.ckms.entity.InventoryTransaction.builder()
                        .warehouse(warehouse)
                        .type(com.swp.ckms.enums.InventoryTransactionType.PRODUCTION_ADD)
                        .product(product) // Set product field
                        .quantity(producedQty)
                        .refId(planId)
                        .note("Thêm thành phẩm từ kế hoạch sản xuất: " + plan.getBatchCode())
                        .build();
                inventoryTransactionRepository.save(tx);
            }
        }

        // Step 8: Return response
        return ProductionPlanResponse.builder()
                .planId(plan.getPlanId())
                .planName(plan.getPlanName())
                .batchCode(plan.getBatchCode())
                .kitchenId(plan.getKitchen().getKitchenId())
                .status(plan.getStatus().name())
                .createdAt(plan.getCreatedAt())
                .coordinatorUserId(plan.getCoordinatorUser().getUserId())
                .build();
    }

    @Override
    public ProductionPlanResponse cancelProductionPlan(Long planId, Long requestVersion, boolean returnInventory) {
        // Step 1: Security & Identity validation
        UserContext ctx = SecurityUtils.getCurrentUserContext();
        if (ctx == null) {
            throw new AccessDeniedException("User context not found or not authenticated");
        }
        User currentUser = userRepository.findById(ctx.getUserId())
                .orElseThrow(() -> new AccessDeniedException("User not found in system"));

        boolean isSystemScope = "SYSTEM".equalsIgnoreCase(ctx.getScope());
        ProductionPlan plan;

        // Step 2: Fetch ProductionPlan with Kitchen Check
        if (!isSystemScope) {
            if (currentUser.getKitchen() == null) {
                throw new AccessDeniedException("You do not have permission to modify this Production Plan.");
            }
            plan = productionPlanRepository.findByPlanIdAndKitchen_KitchenId(planId, currentUser.getKitchen().getKitchenId())
                    .orElseThrow(() -> new com.swp.ckms.exception.business.ResourceNotFoundException("Production Plan not found or you don't have access. ID: " + planId));
        } else {
            plan = productionPlanRepository.findById(planId)
                    .orElseThrow(() -> new com.swp.ckms.exception.business.ResourceNotFoundException("Production Plan not found with id: " + planId));
        }

        // Step 3: Idempotency Check
        if (plan.getStatus() == ProductionPlanStatus.CANCELLED) {
            return ProductionPlanResponse.builder()
                    .planId(plan.getPlanId())
                    .planName(plan.getPlanName())
                    .batchCode(plan.getBatchCode())
                    .kitchenId(plan.getKitchen().getKitchenId())
                    .status(plan.getStatus().name())
                    .createdAt(plan.getCreatedAt())
                    .coordinatorUserId(plan.getCoordinatorUser().getUserId())
                    .build();
        }

        // Step 4: State Guard - Cannot cancel finished plan
        if (plan.getStatus() == ProductionPlanStatus.FINISHED) {
            throw new OrderAssignmentConflictException("Cannot cancel a FINISHED Production Plan.");
        }

        // Step 5: Audit Return Logic (If plan was IN_PRODUCTION and user wants to return)
        if (returnInventory && plan.getStatus() == ProductionPlanStatus.IN_PRODUCTION) {
            // Check for Double Return Protection
            boolean alreadyReturned = inventoryTransactionRepository.existsByTypeAndRefId(
                    com.swp.ckms.enums.InventoryTransactionType.PRODUCTION_RETURN, planId);

            if (!alreadyReturned) {
                // Get all DEDUCT transactions for this plan
                List<com.swp.ckms.entity.InventoryTransaction> deductTransactions = inventoryTransactionRepository
                        .findByRefIdAndType(planId, com.swp.ckms.enums.InventoryTransactionType.PRODUCTION_DEDUCT);

                for (com.swp.ckms.entity.InventoryTransaction tx : deductTransactions) {
                    // Find or Restore Stock item with Pessimistic Lock
                    // Logic: Normally we'd find an item with same Material + Expiry + Warehouse.
                    // If not exists, we might need to recreate it.
                    // For now, let's assume we try to find a compatible one first.
                    
                    List<com.swp.ckms.entity.KitchenStockItem> targetItems = kitchenStockItemRepository.lockMaterialsForDeduction(
                            tx.getWarehouse().getWarehouseId(), 
                            java.util.Collections.singletonList(tx.getMaterial().getId())
                    ).stream()
                    .filter(i -> i.getExpiryDate() == null ? tx.getMaterial().getId() != null : true ) // Simplified check
                    .collect(java.util.stream.Collectors.toList());
                    
                    com.swp.ckms.entity.KitchenStockItem targetItem = null;
                    // Try to find exact match on expiry date (could be null)
                    for (com.swp.ckms.entity.KitchenStockItem k : targetItems) {
                        if (java.util.Objects.equals(k.getExpiryDate(), k.getExpiryDate())) { // This is placeholder logic, need real match
                             // We should probably have a specific find-and-lock by warehouse, material, expiry
                        }
                    }
                    
                    // IF NO MATCH, CREATE NEW. IF MATCH, ADD QUANTITY.
                    // Simplified: Since we want to be strict, let's just create a new row or add to first available 
                    // that matches material + warehouse + (ideally) expiry.
                    
                    // Actually, the most robust way in the current schema without an 'original_stock_item_id' 
                    // is to create a new record representing the returned goods to maintain the return's identity.
                    kitchenStockItemRepository.save(com.swp.ckms.entity.KitchenStockItem.builder()
                            .warehouse(tx.getWarehouse())
                            .material(tx.getMaterial())
                            .quantity(tx.getQuantity().abs()) // Restore the absolute value
                            .expiryDate(null) // Ideally we'd store expiry in Transaction too, or trace back. 
                            .build());

                    // Record Audit Return
                    inventoryTransactionRepository.save(com.swp.ckms.entity.InventoryTransaction.builder()
                            .warehouse(tx.getWarehouse())
                            .material(tx.getMaterial())
                            .quantity(tx.getQuantity().abs())
                            .type(com.swp.ckms.enums.InventoryTransactionType.PRODUCTION_RETURN)
                            .refId(planId)
                            .refLineId(tx.getRefLineId())
                            .note("Auto-return from cancelled production plan")
                            .createdAt(java.time.LocalDateTime.now())
                            .build());
                }
            }
        }

        // Step 6: Atomic Release Orders
        storeOrderRepository.releaseOrdersFromPlan(planId);

        // Step 7: Update Plan Status
        if (requestVersion != null) {
            plan.setVersion(requestVersion);
        }
        plan.setStatus(ProductionPlanStatus.CANCELLED);
        productionPlanRepository.save(plan);

        // Step 8: Return Response
        return ProductionPlanResponse.builder()
                .planId(plan.getPlanId())
                .planName(plan.getPlanName())
                .batchCode(plan.getBatchCode())
                .kitchenId(plan.getKitchen().getKitchenId())
                .status(plan.getStatus().name())
                .createdAt(plan.getCreatedAt())
                .coordinatorUserId(plan.getCoordinatorUser().getUserId())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<com.swp.ckms.dto.response.ProductionPlanSummaryResponse> getAllProductionPlans(
            com.swp.ckms.enums.ProductionPlanStatus status, org.springframework.data.domain.Pageable pageable) {
        
        UserContext ctx = SecurityUtils.getCurrentUserContext();
        if (ctx == null) throw new AccessDeniedException("Unauthorized");
        
        User currentUser = userRepository.findById(ctx.getUserId())
                .orElseThrow(() -> new AccessDeniedException("User not found"));

        org.springframework.data.jpa.domain.Specification<ProductionPlan> spec = 
                org.springframework.data.jpa.domain.Specification.where(
                        com.swp.ckms.repository.specification.ProductionPlanSpecification.hasStatus(status)
                );

        // Security Scope: STAFF only see their kitchen
        boolean isSystemScope = "SYSTEM".equalsIgnoreCase(ctx.getScope());

        if (!isSystemScope) {
            if (currentUser.getKitchen() == null) {
                return org.springframework.data.domain.Page.empty(pageable);
            }
            spec = spec.and(com.swp.ckms.repository.specification.ProductionPlanSpecification.hasKitchenId(
                    currentUser.getKitchen().getKitchenId()));
        }

        return productionPlanRepository.findAll(spec, pageable)
                .map(this::mapToSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public com.swp.ckms.dto.response.ProductionPlanDetailResponse getProductionPlanDetail(Long planId) {
        UserContext ctx = SecurityUtils.getCurrentUserContext();
        if (ctx == null) throw new AccessDeniedException("Unauthorized");
        
        User currentUser = userRepository.findById(ctx.getUserId())
                .orElseThrow(() -> new AccessDeniedException("User not found"));

        // Optimized fetch with JOIN FETCH
        ProductionPlan plan = productionPlanRepository.findByIdWithMaterials(planId)
                .orElseThrow(() -> new com.swp.ckms.exception.business.ResourceNotFoundException("Production Plan not found with id: " + planId));

        // Security Scope: 404 Not Found if mismatch kitchen for STAFF
        boolean isSystemScope = "SYSTEM".equalsIgnoreCase(ctx.getScope());

        if (!isSystemScope) {
            if (currentUser.getKitchen() == null || 
                !currentUser.getKitchen().getKitchenId().equals(plan.getKitchen().getKitchenId())) {
                throw new com.swp.ckms.exception.business.ResourceNotFoundException("Production Plan not found with id: " + planId);
            }
        }

        return com.swp.ckms.dto.response.ProductionPlanDetailResponse.builder()
                .planId(plan.getPlanId())
                .planName(plan.getPlanName())
                .batchCode(plan.getBatchCode())
                .kitchenId(plan.getKitchen().getKitchenId())
                .status(plan.getStatus().name())
                .createdAt(plan.getCreatedAt())
                .coordinatorUserId(plan.getCoordinatorUser().getUserId())
                .materials(plan.getMaterialRequirements().stream()
                        .map(req -> com.swp.ckms.dto.response.MaterialRequirementResponse.builder()
                                .materialId(req.getMaterial().getId())
                                .materialName(req.getMaterial().getName())
                                .requiredQuantity(req.getRequiredQuantity())
                                .build())
                        .collect(java.util.stream.Collectors.toList()))
                .build();
    }

    private com.swp.ckms.dto.response.ProductionPlanSummaryResponse mapToSummaryResponse(ProductionPlan plan) {
        return com.swp.ckms.dto.response.ProductionPlanSummaryResponse.builder()
                .planId(plan.getPlanId())
                .planName(plan.getPlanName())
                .batchCode(plan.getBatchCode())
                .kitchenId(plan.getKitchen().getKitchenId())
                .status(plan.getStatus().name())
                .createdAt(plan.getCreatedAt())
                .coordinatorUserId(plan.getCoordinatorUser().getUserId())
                .build();
    }
}
