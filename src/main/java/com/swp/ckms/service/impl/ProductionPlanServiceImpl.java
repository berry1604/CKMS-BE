package com.swp.ckms.service.impl;

import com.swp.ckms.dto.request.ProductionPlanRequest;
import com.swp.ckms.dto.response.ProductionPlanResponse;
import com.swp.ckms.entity.CentralKitchen;
import com.swp.ckms.entity.ProductionPlan;
import com.swp.ckms.entity.ProductionPlanMaterialRequirement;
import com.swp.ckms.entity.User;
import com.swp.ckms.enums.ProductionPlanStatus;
import com.swp.ckms.exception.business.OrderAssignmentConflictException;
import com.swp.ckms.repository.ProductionOutputRepository;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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
    private final ProductionOutputRepository productionOutputRepository;

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
        if (kitchen == null || kitchen.getKitchenId() == null) {
            throw new AccessDeniedException("User does not belong to any Central Kitchen and cannot create a Production Plan.");
        }

        List<Long> orderIds = java.util.Objects.requireNonNull(request.getStoreOrderIds(), "Order IDs list cannot be null");

        // BR-02: Check Production Capacity (Senior Logic: Check all commitments for the date)
        if (kitchen.getMaxDailyCapacity() != null) {
            java.math.BigDecimal globalLoad = storeOrderRepository.sumQuantityByKitchenAndDeliveryDate(
                    kitchen.getKitchenId(), request.getPlannedDate());
            if (globalLoad == null) globalLoad = java.math.BigDecimal.ZERO;

            if (globalLoad.compareTo(kitchen.getMaxDailyCapacity()) > 0) {
                throw new com.swp.ckms.exception.business.BusinessRuleViolationException(String.format(
                        "Không thể tạo kế hoạch: Tổng tải cam kết ngày %s (%s) đã vượt quá công suất bếp (%s)",
                        request.getPlannedDate(), globalLoad, kitchen.getMaxDailyCapacity()));
            }
        }

        // Generate Plan Name
        String planName = "Plan_" + LocalDateTime.now().toString().replace(":", "-");

        // Step 2: Create ProductionPlan (NOT committed yet)
        ProductionPlan plan = ProductionPlan.builder()
                .kitchen(kitchen)
                .coordinatorUser(currentUser)
                .planName(planName)
                .plannedDate(request.getPlannedDate())
                .status(ProductionPlanStatus.PLANNED)
                .createdAt(LocalDateTime.now())
                .build();

        ProductionPlan savedPlan = java.util.Objects.requireNonNull(productionPlanRepository.save(plan), "Saved plan cannot be null");

        // Step 3: Atomic Assign Orders
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

        if (plan.getKitchen() == null) {
            throw new IllegalStateException("Production Plan has no kitchen assigned.");
        }

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
            if (currentUser.getKitchen() == null || plan.getKitchen().getKitchenId() == null || !currentUser.getKitchen().getKitchenId().equals(plan.getKitchen().getKitchenId())) {
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
                .map(req -> java.util.Objects.requireNonNull(req.getMaterial(), "Material requirement has no material").getId())
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
                java.math.BigDecimal itemReserved = item.getReservedQuantity() != null ? item.getReservedQuantity() : java.math.BigDecimal.ZERO;
                
                if (itemQty.compareTo(java.math.BigDecimal.ZERO) <= 0) continue;
                
                java.math.BigDecimal deductedQty;
                if (itemQty.compareTo(requiredQty) <= 0) {
                    // Exhaust this item
                    deductedQty = itemQty;
                    requiredQty = requiredQty.subtract(itemQty);
                    item.setQuantity(java.math.BigDecimal.ZERO);
                    // Also clear reservation if any
                    item.setReservedQuantity(java.math.BigDecimal.ZERO); 
                } else {
                    // Partially use this item
                    deductedQty = requiredQty;
                    item.setQuantity(itemQty.subtract(requiredQty));
                    // Reduce reserved if it was reserved
                    if (itemReserved.compareTo(deductedQty) >= 0) {
                        item.setReservedQuantity(itemReserved.subtract(deductedQty));
                    } else {
                        item.setReservedQuantity(java.math.BigDecimal.ZERO);
                    }
                    requiredQty = java.math.BigDecimal.ZERO;
                }

                // RECORD AUDIT TRANSACTION
                inventoryTransactionRepository.save(com.swp.ckms.entity.InventoryTransaction.builder()
                        .kitchenWarehouse(warehouse)
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
        
        // [Phase 2] Just-in-Time Locking: Lock associated Store Orders
        storeOrderRepository.updateStatusByPlanId(planId, com.swp.ckms.enums.OrderStatus.LOCKED);

        // Step 10 & 11: Save plan (triggers version check) and let dirty checking save KitchenStockItem
        productionPlanRepository.save(plan);

        // Step 12: Return response
        return buildProductionPlanResponse(plan);
    }

    @Override
    @Transactional
    public ProductionPlanResponse reportProductionYield(Long planId, com.swp.ckms.dto.request.FinishProductionPlanRequest request) {
        // Step 1: Security & Identity validation
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
                    .orElseThrow(() -> new com.swp.ckms.exception.business.ResourceNotFoundException("Production Plan not found or access denied. ID: " + planId));
        } else {
            plan = productionPlanRepository.findById(planId)
                    .orElseThrow(() -> new com.swp.ckms.exception.business.ResourceNotFoundException("Production Plan not found with id: " + planId));
        }

        // Step 3: Idempotency Check
        if (plan.getStatus() == ProductionPlanStatus.PRODUCED || plan.getStatus() == ProductionPlanStatus.FINISHED) {
            return buildProductionPlanResponse(plan);
        }

        // Step 4: State Guard
        if (plan.getStatus() != ProductionPlanStatus.IN_PRODUCTION) {
            throw new OrderAssignmentConflictException("Production Plan is not in IN_PRODUCTION state. Current state: " + plan.getStatus());
        }

        // Step 5: Version Check (Optimistic Locking)
        if (request.getRequestVersion() != null) {
            plan.setVersion(request.getRequestVersion());
        }

        // Step 6: Identify Default Warehouse
        com.swp.ckms.entity.KitchenWarehouse warehouse = warehouseRepository.findByKitchen_KitchenId(plan.getKitchen().getKitchenId())
                .stream().findFirst()
                .orElseThrow(() -> new com.swp.ckms.exception.business.ResourceNotFoundException("No default warehouse found for kitchen: " + plan.getKitchen().getKitchenId()));

        // Step 7: Record Production Output (Yield Reporting)
        if (request.getOutputs() == null || request.getOutputs().isEmpty()) {
            throw new IllegalArgumentException("Production outputs must be provided to finish the plan.");
        }

        for (com.swp.ckms.dto.request.ProductionOutputRequest outputReq : request.getOutputs()) {
            com.swp.ckms.entity.Product product = com.swp.ckms.entity.Product.builder().id(outputReq.getProductId()).build();
            
            // Record to ProductionOutput entity
            productionOutputRepository.save(com.swp.ckms.entity.ProductionOutput.builder()
                    .productionPlan(plan)
                    .product(product)
                    .actualProducedQty(outputReq.getActualQty())
                    .build());

            // Feedback Loop: Add produced items to Kitchen Stock
            com.swp.ckms.entity.KitchenStockItem stockItem = kitchenStockItemRepository
                    .findByWarehouse_WarehouseIdAndProduct_Id(warehouse.getWarehouseId(), product.getId())
                    .stream().findFirst()
                    .orElseGet(() -> com.swp.ckms.entity.KitchenStockItem.builder()
                            .warehouse(warehouse)
                            .product(product)
                            .quantity(BigDecimal.ZERO)
                            .reservedQuantity(BigDecimal.ZERO)
                            .build());

            stockItem.setQuantity(stockItem.getQuantity().add(outputReq.getActualQty()));
            stockItem.setProductionPlan(plan);
            if (plan.getPlannedDate() != null) {
                stockItem.setExpiryDate(plan.getPlannedDate().plusDays(3));
            }
            kitchenStockItemRepository.save(stockItem);

            // Audit
            inventoryTransactionRepository.save(com.swp.ckms.entity.InventoryTransaction.builder()
                    .kitchenWarehouse(warehouse)
                    .type(com.swp.ckms.enums.InventoryTransactionType.PRODUCTION_ADD)
                    .product(product)
                    .quantity(outputReq.getActualQty())
                    .refId(planId)
                    .expiryDate(stockItem.getExpiryDate())
                    .createdAt(java.time.LocalDateTime.now())
                    .build());
        }

        // Step 8: Update Plan Status to PRODUCED (Awaiting Allocation)
        plan.setStatus(ProductionPlanStatus.PRODUCED);
        productionPlanRepository.save(plan);

        return buildProductionPlanResponse(plan);
    }


    private ProductionPlanResponse buildProductionPlanResponse(ProductionPlan plan) {
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
                            tx.getKitchenWarehouse().getWarehouseId(), 
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
                    
                    // Actually, the most robust way in the current schema without an 'original_stock_item_id' 
                    // is to create a new record representing the returned goods to maintain the return's identity.
                    kitchenStockItemRepository.save(com.swp.ckms.entity.KitchenStockItem.builder()
                            .warehouse(tx.getKitchenWarehouse())
                            .material(tx.getMaterial())
                            .quantity(tx.getQuantity().abs()) // Restore the absolute value
                            .expiryDate(null) // Ideally we'd store expiry in Transaction too, or trace back. 
                            .build());

                    // Record Audit Return
                    inventoryTransactionRepository.save(com.swp.ckms.entity.InventoryTransaction.builder()
                            .kitchenWarehouse(tx.getKitchenWarehouse())
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
