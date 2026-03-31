package com.swp.ckms.service.impl;

import java.util.Objects;
import com.swp.ckms.dto.request.FinishProductionPlanRequest;
import com.swp.ckms.dto.request.ProductionOutputRequest;
import com.swp.ckms.dto.request.ProductionPlanRequest;
import com.swp.ckms.dto.response.*;
import com.swp.ckms.entity.*;
import com.swp.ckms.enums.InventoryTransactionType;
import com.swp.ckms.enums.OrderStatus;
import com.swp.ckms.enums.ProductionPlanStatus;
import com.swp.ckms.exception.business.BusinessRuleViolationException;
import com.swp.ckms.exception.business.InsufficientMaterialException;
import com.swp.ckms.exception.business.OrderAssignmentConflictException;
import com.swp.ckms.exception.business.ResourceNotFoundException;
import com.swp.ckms.repository.*;
import com.swp.ckms.repository.projection.MaterialRequirementProjection;
import com.swp.ckms.repository.projection.MaterialStockProjection;
import com.swp.ckms.repository.specification.ProductionPlanSpecification;
import com.swp.ckms.security.SecurityUtils;
import com.swp.ckms.security.UserContext;
import com.swp.ckms.service.ProductionPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
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
    private final KitchenWarehouseRepository warehouseRepository;
    private final KitchenStockItemRepository kitchenStockItemRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final ProductionOutputRepository productionOutputRepository;
    private final com.swp.ckms.service.DispatchService dispatchService;
    private final CentralKitchenRepository kitchenRepository;

    @Override
    public ProductionPlanResponse createProductionPlan(ProductionPlanRequest request) {
        // Step 1: Get Current User & Kitchen
        UserContext ctx = SecurityUtils.getCurrentUserContext();
        if (ctx == null) {
            throw new AccessDeniedException("User context not found or not authenticated");
        }

        User currentUser = userRepository.findById(ctx.getUserId())
                .orElseThrow(() -> new AccessDeniedException("User not found in system"));

        CentralKitchen kitchen;
        if (request.getKitchenId() != null) {
            // Priority 1: Use kitchenId from request (HQ or flexible mode)
            kitchen = kitchenRepository.findById(request.getKitchenId())
                    .orElseThrow(() -> new ResourceNotFoundException("Kitchen not found with ID: " + request.getKitchenId()));
            
            // Security Check: Only HQ or ADMIN can pick a DIFFERENT kitchen than their assigned one
            if (!ctx.getUserId().equals(1L)) { // Simplified: ID 1 is Super Admin
                if (currentUser.getKitchen() != null && 
                    !currentUser.getKitchen().getKitchenId().equals(kitchen.getKitchenId())) {
                    // Check if they have MANAGER role/privilege
                    boolean isHq = currentUser.getRole().getRoleName().equals("ADMIN") || 
                                   currentUser.getRole().getRoleName().equals("MANAGER");
                    if (!isHq) {
                        throw new AccessDeniedException("You don't have permission to create a plan for another kitchen.");
                    }
                }
            }
            if (kitchen.getIsActive() != null && !kitchen.getIsActive()) {
                throw new BusinessRuleViolationException("Không thể tạo kế hoạch sản xuất cho bếp đang ngừng hoạt động.");
            }
        } else {
            // Priority 2: Fallback to user's assigned kitchen
            kitchen = currentUser.getKitchen();
            if (kitchen == null) {
                // Single Kitchen Refactor: Default to Kitchen ID 1 for system-level users (Coordinator/Manager)
                kitchen = kitchenRepository.findById(1L)
                        .orElseThrow(() -> new ResourceNotFoundException("Default Kitchen not found with ID 1"));
            }
        }

        List<Long> orderIds = request.getStoreOrderIds();
        if (orderIds == null || orderIds.isEmpty()) {
            log.info("ProductionPlanRequest.storeOrderIds is empty. Invoking DispatchService for auto-suggestion for kitchen {} on {}", kitchen.getKitchenId(), request.getPlannedDate());
            com.swp.ckms.dto.response.DispatchSuggestionResponse suggestion = dispatchService.suggestProductionPlan(kitchen.getKitchenId(), request.getPlannedDate());
            orderIds = suggestion.getProducts().stream()
                    .flatMap(p -> p.getAllocations().stream())
                    .filter(a -> a.getAllocatedQty().compareTo(java.math.BigDecimal.ZERO) > 0)
                    .map(com.swp.ckms.dto.response.DispatchSuggestionResponse.OrderAllocationSuggestion::getOrderId)
                    .distinct()
                    .collect(Collectors.toList());
            
            if (orderIds.isEmpty()) {
                throw new BusinessRuleViolationException("Không có đơn hàng nào hợp lệ để tạo kế hoạch tự động cho ngày " + request.getPlannedDate());
            }
        }

        // BR-02: Check Production Capacity (Senior Logic: Check all commitments for the date)
        if (kitchen.getMaxDailyCapacity() != null) {
            java.math.BigDecimal globalLoad = storeOrderRepository.sumQuantityByKitchenAndDeliveryDate(
                    kitchen.getKitchenId(), request.getPlannedDate());
            if (globalLoad == null) globalLoad = java.math.BigDecimal.ZERO;

            if (globalLoad.compareTo(kitchen.getMaxDailyCapacity()) > 0) {
                throw new BusinessRuleViolationException(String.format(
                        "Không thể tạo kế hoạch: Tổng tải cam kết ngày %s (%s) đã vượt quá công suất bếp (%s)",
                        request.getPlannedDate(), globalLoad, kitchen.getMaxDailyCapacity()));
            }
        }

        // Generate Plan Name
        String planName = "Plan_" + LocalDateTime.now().toString().replace(":", "-");

        // Step 2: Create ProductionPlan (NOT committed yet)
        long dailyCount = productionPlanRepository.countByPlannedDateAndKitchen_KitchenId(
                request.getPlannedDate(), kitchen.getKitchenId());
        String batchCode = String.format("BATCH-%s-K%d-%03d",
                request.getPlannedDate().toString().replace("-", ""),
                kitchen.getKitchenId(),
                dailyCount + 1);

        ProductionPlan plan = ProductionPlan.builder()
                .kitchen(kitchen)
                .coordinatorUser(currentUser)
                .planName(planName)
                .batchCode(batchCode)
                .plannedDate(request.getPlannedDate())
                .status(ProductionPlanStatus.PLANNED)
                .createdAt(LocalDateTime.now())
                .build();

        ProductionPlan savedPlan = Objects.requireNonNull(productionPlanRepository.save(plan), "Saved plan cannot be null");

        // Step 3: Atomic Assign Orders
        int updatedRows = storeOrderRepository.assignOrdersToPlan(savedPlan, orderIds);

        // Row Count Validation
        if (updatedRows != orderIds.size()) {
            throw new OrderAssignmentConflictException("Một hoặc nhiều Order đã được xử lý bởi người khác hoặc không ở trạng thái CONFIRMED/APPROVED. Vui lòng reload!");
        }

        // Step 4: Snapshot Material Requirement
        List<MaterialRequirementProjection> requirements = storeOrderRepository.getMaterialRequirementsForPlan(savedPlan.getPlanId());

        if (requirements.isEmpty()) {
            log.warn("No material requirements found for Plan ID {}", savedPlan.getPlanId());
        }

        // Step 5: Save Snapshots & RESERVE Inventory
        KitchenWarehouse warehouse = warehouseRepository.findByKitchen_KitchenId(kitchen.getKitchenId())
                .stream().findFirst().orElse(null);

        for (MaterialRequirementProjection requirement : requirements) {
            ProductionPlanMaterialRequirement snapshot = ProductionPlanMaterialRequirement.builder()
                    .plan(savedPlan)
                    .material(requirement.getMaterial())
                    .requiredQuantity(requirement.getTotal())
                    .build();
            materialRequirementRepository.save(snapshot);

            // RESERVE Logic (FIFO Reservation)
            if (warehouse != null) {
                reserveMaterials(warehouse.getWarehouseId(), requirement.getMaterial().getId(), requirement.getTotal());
            }
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

    private void reserveMaterials(Long warehouseId, Long materialId, BigDecimal totalRequired) {
        List<KitchenStockItem> stockItems = kitchenStockItemRepository.lockMaterialsForDeduction(warehouseId, Collections.singletonList(materialId));
        BigDecimal remainingToReserve = totalRequired;

        for (KitchenStockItem item : stockItems) {
            if (remainingToReserve.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal currentReserved = item.getReservedQuantity() != null ? item.getReservedQuantity() : BigDecimal.ZERO;
            BigDecimal itemAvailable = item.getQuantity().subtract(currentReserved);
            if (itemAvailable.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal reservedInThisItem = itemAvailable.min(remainingToReserve);
            item.setReservedQuantity(currentReserved.add(reservedInThisItem));
            remainingToReserve = remainingToReserve.subtract(reservedInThisItem);
        }
        
        if (remainingToReserve.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal actualAvailable = totalRequired.subtract(remainingToReserve);
            throw new InsufficientMaterialException(
                "Không đủ nguyên liệu để reserve. Material ID: " + materialId + ", cần: " + totalRequired + ", khả dụng: " + actualAvailable,
                List.of(com.swp.ckms.dto.response.MissingMaterialResponse.builder()
                    .materialId(materialId)
                    .requiredQuantity(totalRequired)
                    .availableQuantity(actualAvailable)
                    .missingQuantity(remainingToReserve)
                    .build())
            );
        }
    }

    @Override
    public ProductionPlanResponse checkAndReadyPlan(Long planId) {
        // Step 1: Fetch ProductionPlan
        ProductionPlan plan = productionPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Production Plan not found with id: " + planId));

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
        KitchenWarehouse warehouse = warehouseRepository.findByKitchen_KitchenId(plan.getKitchen().getKitchenId())
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No default warehouse found for Kitchen ID: " + plan.getKitchen().getKitchenId()));

        // Step 5: Load List ProductionPlanMaterialRequirement (Snapshot)
        List<ProductionPlanMaterialRequirement> requirements = materialRequirementRepository.findByPlan_PlanId(planId);
        if (requirements.isEmpty()) {
            throw new OrderAssignmentConflictException("Plan has no material requirements. Cannot be ready to produce.");
        }

        // Step 6: Extract list of materialId
        List<Long> materialIds = requirements.stream()
                .map(req -> Objects.requireNonNull(req.getMaterial(), "Material requirement has no material").getId())
                .collect(Collectors.toList());

        // Step 7: Lấy Map Stock
        List<MaterialStockProjection> availableStocks = kitchenStockItemRepository.getAvailableStockForMaterials(warehouse.getWarehouseId(), materialIds);
        Map<Long, BigDecimal> stockMap = availableStocks.stream()
                .collect(Collectors.toMap(
                        MaterialStockProjection::getMaterialId,
                        MaterialStockProjection::getTotalQuantity,
                        BigDecimal::add // In case of duplicates, though logic shouldn't produce them
                ));

        // Step 8 & 9: Lặp và so sánh từng material
        List<MissingMaterialResponse> missingMaterials = new ArrayList<>();
        
        for (ProductionPlanMaterialRequirement req : requirements) {
            Long matId = req.getMaterial().getId();
            BigDecimal requiredQty = req.getRequiredQuantity();
            BigDecimal availableQty = stockMap.getOrDefault(matId, BigDecimal.ZERO);
            
            if (availableQty.compareTo(requiredQty) < 0) {
                missingMaterials.add(MissingMaterialResponse.builder()
                        .materialId(matId)
                        .materialName(req.getMaterial().getName())
                        .requiredQuantity(requiredQty)
                        .availableQuantity(availableQty)
                        .missingQuantity(requiredQty.subtract(availableQty))
                        .build());
            }
        }

        if (!missingMaterials.isEmpty()) {
            throw new InsufficientMaterialException("Not enough materials to ready the Production Plan.", missingMaterials);
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
                    .orElseThrow(() -> new ResourceNotFoundException("Production Plan not found or you don't have access. ID: " + planId));
        } else {
             plan = productionPlanRepository.findById(planId)
                    .orElseThrow(() -> new ResourceNotFoundException("Production Plan not found with id: " + planId));
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
        KitchenWarehouse warehouse = warehouseRepository.findByKitchen_KitchenId(plan.getKitchen().getKitchenId())
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No default warehouse found for Kitchen ID: " + plan.getKitchen().getKitchenId()));

        // Step 7: Load List ProductionPlanMaterialRequirement (Snapshot)
        List<ProductionPlanMaterialRequirement> requirements = materialRequirementRepository.findByPlan_PlanId(planId);
        if (requirements.isEmpty()) {
            throw new OrderAssignmentConflictException("Plan has no material requirements. Cannot start producing.");
        }

        List<Long> materialIds = requirements.stream()
                .map(req -> req.getMaterial().getId())
                .collect(Collectors.toList());

        // Step 8: Lock Materials for Deduction (PESSIMISTIC_WRITE + ORDER BY materialId, expiryDate NULLS LAST, id)
        List<KitchenStockItem> lockedItems = kitchenStockItemRepository.lockMaterialsForDeduction(warehouse.getWarehouseId(), materialIds);

        // Group locked items by materialId for easier FIFO deduction
        Map<Long, List<KitchenStockItem>> itemsByMaterial = lockedItems.stream()
                .collect(Collectors.groupingBy(item -> item.getMaterial().getId()));

        List<MissingMaterialResponse> missingMaterials = new ArrayList<>();

        // Step 9: FIFO Deduct Algorithm
        for (ProductionPlanMaterialRequirement req : requirements) {
            Long matId = req.getMaterial().getId();
            BigDecimal requiredQty = req.getRequiredQuantity();
            
            List<KitchenStockItem> availableItemsForMatId = itemsByMaterial.getOrDefault(matId, Collections.emptyList());
            
            for (KitchenStockItem item : availableItemsForMatId) {
                if (requiredQty.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                
                BigDecimal itemQty = item.getQuantity();
                BigDecimal itemReserved = item.getReservedQuantity() != null ? item.getReservedQuantity() : BigDecimal.ZERO;
                
                if (itemQty.compareTo(BigDecimal.ZERO) <= 0) continue;
                
                BigDecimal deductedQty;
                if (itemQty.compareTo(requiredQty) <= 0) {
                    // Exhaust this item
                    deductedQty = itemQty;
                    requiredQty = requiredQty.subtract(itemQty);
                    
                    // Deduction from both Physical and Reserved
                    item.setQuantity(BigDecimal.ZERO);
                    
                    if (itemReserved.compareTo(deductedQty) >= 0) {
                        item.setReservedQuantity(itemReserved.subtract(deductedQty));
                    } else {
                        item.setReservedQuantity(BigDecimal.ZERO);
                    }
                } else {
                    // Partially use this item
                    deductedQty = requiredQty;
                    item.setQuantity(itemQty.subtract(requiredQty));
                    
                    if (itemReserved.compareTo(deductedQty) >= 0) {
                        item.setReservedQuantity(itemReserved.subtract(deductedQty));
                    } else {
                        item.setReservedQuantity(BigDecimal.ZERO);
                    }
                    requiredQty = BigDecimal.ZERO;
                }

                // RECORD AUDIT TRANSACTION
                inventoryTransactionRepository.save(InventoryTransaction.builder()
                        .kitchenWarehouse(warehouse)
                        .material(req.getMaterial())
                        .quantity(deductedQty.negate()) // Negative for deduction
                        .type(InventoryTransactionType.PRODUCTION_DEDUCT)
                        .refId(planId)
                        .refLineId(req.getId())
                        .expiryDate(item.getExpiryDate()) // Lưu expiry để cancel có thể match lại đúng stock item
                        .createdAt(LocalDateTime.now())
                        .build());
            }
            
            // If requiredQty > 0 after checking all items, we are short on this material
            if (requiredQty.compareTo(BigDecimal.ZERO) > 0) {
                 BigDecimal availableQtyForMatId = availableItemsForMatId.stream()
                        .map(KitchenStockItem::getQuantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .add(req.getRequiredQuantity().subtract(requiredQty)); 

                 missingMaterials.add(MissingMaterialResponse.builder()
                        .materialId(matId)
                        .materialName(req.getMaterial().getName())
                        .requiredQuantity(req.getRequiredQuantity())
                        .availableQuantity(availableQtyForMatId)
                        .missingQuantity(requiredQty)
                        .build());
            }
        }

        if (!missingMaterials.isEmpty()) {
            throw new InsufficientMaterialException("Not enough materials to start the Production Plan.", missingMaterials);
        }

        // Step 11: Cập nhật plan.setStatus(IN_PRODUCTION)
        plan.setStatus(ProductionPlanStatus.IN_PRODUCTION);
        
        // [Phase 2] Just-in-Time Locking: Lock associated Store Orders
        storeOrderRepository.updateStatusByPlanId(planId, OrderStatus.LOCKED);

        // Step 10 & 11: Save plan (triggers version check) and let dirty checking save KitchenStockItem
        productionPlanRepository.save(plan);

        // Step 12: Return response
        return buildProductionPlanResponse(plan);
    }

    @Override
    @Transactional
    public ProductionPlanResponse reportProductionYield(Long planId, FinishProductionPlanRequest request) {
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
                    .orElseThrow(() -> new ResourceNotFoundException("Production Plan not found or access denied. ID: " + planId));
        } else {
            plan = productionPlanRepository.findById(planId)
                    .orElseThrow(() -> new ResourceNotFoundException("Production Plan not found with id: " + planId));
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
        KitchenWarehouse warehouse = warehouseRepository.findByKitchen_KitchenId(plan.getKitchen().getKitchenId())
                .stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No default warehouse found for kitchen: " + plan.getKitchen().getKitchenId()));

        // Step 7: Record Production Output (Yield Reporting)
        if (request.getOutputs() == null || request.getOutputs().isEmpty()) {
            throw new IllegalArgumentException("Production outputs must be provided to finish the plan.");
        }

        for (ProductionOutputRequest outputReq : request.getOutputs()) {
            if (outputReq.getActualQty() == null) {
                throw new IllegalArgumentException("Actual quantity for product ID " + outputReq.getProductId() + " cannot be null");
            }
            Product product = Product.builder().id(outputReq.getProductId()).build();
            
            // Record to ProductionOutput entity
            productionOutputRepository.save(ProductionOutput.builder()
                    .productionPlan(plan)
                    .product(product)
                    .actualProducedQty(outputReq.getActualQty())
                    .build());

            // Feedback Loop: Add produced items to Kitchen Stock as a NEW batch record (Lot-based tracking)
            KitchenStockItem stockItem = KitchenStockItem.builder()
                    .warehouse(warehouse)
                    .product(product)
                    .quantity(outputReq.getActualQty())
                    .reservedQuantity(BigDecimal.ZERO)
                    .productionPlan(plan)
                    .expiryDate(plan.getPlannedDate() != null ? plan.getPlannedDate().plusDays(3) : null)
                    .build();
            
            kitchenStockItemRepository.save(stockItem);

            // Audit
            inventoryTransactionRepository.save(InventoryTransaction.builder()
                    .kitchenWarehouse(warehouse)
                    .type(InventoryTransactionType.PRODUCTION_ADD)
                    .product(product)
                    .quantity(outputReq.getActualQty())
                    .refId(planId)
                    .expiryDate(stockItem.getExpiryDate())
                    .createdAt(LocalDateTime.now())
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
                    .orElseThrow(() -> new ResourceNotFoundException("Production Plan not found or you don't have access. ID: " + planId));
        } else {
            plan = productionPlanRepository.findById(planId)
                    .orElseThrow(() -> new ResourceNotFoundException("Production Plan not found with id: " + planId));
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

        // Step 5a: Rollback produced goods (If plan was PRODUCED)
        // Thành phẩm đã nhập kho bếp trong reportProductionYield → cần trừ lại
        if (plan.getStatus() == ProductionPlanStatus.PRODUCED) {
            log.info("Cancelling PRODUCED plan {} — rolling back finished products from kitchen stock", planId);

            KitchenWarehouse warehouse = warehouseRepository.findByKitchen_KitchenId(plan.getKitchen().getKitchenId())
                    .stream().findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("No warehouse found for kitchen: " + plan.getKitchen().getKitchenId()));

            // Tìm tất cả KitchenStockItem được tạo bởi plan này (thành phẩm)
            List<KitchenStockItem> producedStockItems = kitchenStockItemRepository.findByProductionPlan_PlanId(planId);

            for (KitchenStockItem stockItem : producedStockItems) {
                BigDecimal qtyToRemove = stockItem.getQuantity();

                if (qtyToRemove.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                // Trừ quantity về 0 (không xóa record để giữ audit trail)
                stockItem.setQuantity(BigDecimal.ZERO);
                stockItem.setReservedQuantity(BigDecimal.ZERO);
                kitchenStockItemRepository.save(stockItem);

                // Ghi audit transaction: xuất kho ngược thành phẩm
                inventoryTransactionRepository.save(InventoryTransaction.builder()
                        .kitchenWarehouse(warehouse)
                        .product(stockItem.getProduct())
                        .quantity(qtyToRemove.negate()) // Âm = xuất kho
                        .type(InventoryTransactionType.PRODUCTION_RETURN)
                        .refId(planId)
                        .expiryDate(stockItem.getExpiryDate())
                        .productionPlan(plan)
                        .note("Rollback produced goods — cancelled production plan")
                        .createdAt(LocalDateTime.now())
                        .build());
            }
            log.info("Rolled back {} product stock items for plan {}", producedStockItems.size(), planId);
        }

        // Step 5b: Return raw materials (If plan was IN_PRODUCTION and user wants to return)
        // Nguyên liệu đã bị deduct trong startProductionPlan → hoàn trả lại kho
        if (returnInventory && plan.getStatus() == ProductionPlanStatus.IN_PRODUCTION) {
            // Check for Double Return Protection
            boolean alreadyReturned = inventoryTransactionRepository.existsByTypeAndRefId(
                    InventoryTransactionType.PRODUCTION_RETURN, planId);

            if (!alreadyReturned) {
                KitchenWarehouse warehouse = warehouseRepository.findByKitchen_KitchenId(plan.getKitchen().getKitchenId())
                        .stream().findFirst()
                        .orElseThrow(() -> new ResourceNotFoundException("No warehouse found for kitchen: " + plan.getKitchen().getKitchenId()));

                // Get all DEDUCT transactions for this plan
                List<InventoryTransaction> deductTransactions = inventoryTransactionRepository
                        .findByRefIdAndType(planId, InventoryTransactionType.PRODUCTION_DEDUCT);

                for (InventoryTransaction tx : deductTransactions) {
                    BigDecimal returnQty = tx.getQuantity().abs();
                    LocalDate originalExpiry = tx.getExpiryDate();

                    // Tìm stock item gốc theo material + expiry date
                    List<KitchenStockItem> candidates = kitchenStockItemRepository.lockMaterialsForDeduction(
                            warehouse.getWarehouseId(),
                            Collections.singletonList(tx.getMaterial().getId())
                    );

                    KitchenStockItem targetItem = candidates.stream()
                            .filter(k -> Objects.equals(k.getExpiryDate(), originalExpiry))
                            .findFirst()
                            .orElse(null);

                    if (targetItem != null) {
                        // Cộng ngược vào stock item gốc
                        targetItem.setQuantity(targetItem.getQuantity().add(returnQty));
                    } else {
                        // Không tìm được item gốc → tạo mới giữ đúng expiry date
                        kitchenStockItemRepository.save(KitchenStockItem.builder()
                                .warehouse(warehouse)
                                .material(tx.getMaterial())
                                .quantity(returnQty)
                                .reservedQuantity(BigDecimal.ZERO)
                                .expiryDate(originalExpiry)
                                .build());
                    }

                    // Record Audit Return
                    inventoryTransactionRepository.save(InventoryTransaction.builder()
                            .kitchenWarehouse(warehouse)
                            .material(tx.getMaterial())
                            .quantity(returnQty)
                            .type(InventoryTransactionType.PRODUCTION_RETURN)
                            .refId(planId)
                            .refLineId(tx.getRefLineId())
                            .expiryDate(originalExpiry)
                            .note("Auto-return materials from cancelled production plan")
                            .createdAt(LocalDateTime.now())
                            .build());
                }
                log.info("Returned {} material deduction(s) for plan {}", deductTransactions.size(), planId);
            }
        }

        // Step 6: Atomic Release Orders (chỉ revert SCHEDULED và LOCKED → APPROVED)
        int releasedCount = storeOrderRepository.releaseOrdersFromPlan(planId);
        log.info("Released {} orders from plan {}", releasedCount, planId);

        // Step 7: Release Inventory Reservations
        // Chỉ cần release cho PLANNED và READY_TO_PRODUCE (nguyên liệu chỉ reserved, chưa deduct)
        // IN_PRODUCTION và PRODUCED đã deduct rồi → không cần release reservation
        KitchenWarehouse reserveWarehouse = warehouseRepository.findByKitchen_KitchenId(plan.getKitchen().getKitchenId())
                .stream().findFirst().orElse(null);
        if (reserveWarehouse != null && plan.getStatus() != ProductionPlanStatus.IN_PRODUCTION && plan.getStatus() != ProductionPlanStatus.PRODUCED) {
            List<ProductionPlanMaterialRequirement> requirements = materialRequirementRepository.findByPlan_PlanId(planId);
            for (ProductionPlanMaterialRequirement req : requirements) {
                releaseReservation(reserveWarehouse.getWarehouseId(), req.getMaterial().getId(), req.getRequiredQuantity());
            }
        }

        // Step 8: Update Plan Status
        if (requestVersion != null) {
            plan.setVersion(requestVersion);
        }
        plan.setStatus(ProductionPlanStatus.CANCELLED);
        productionPlanRepository.save(plan);

        // Step 9: Return Response
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

    private void releaseReservation(Long warehouseId, Long materialId, BigDecimal qtyToRelease) {
        List<KitchenStockItem> items = kitchenStockItemRepository.lockMaterialsForDeduction(warehouseId, Collections.singletonList(materialId));
        BigDecimal remainingToRelease = qtyToRelease;

        // Try to release from FIFO (opposite of reserve)
        for (KitchenStockItem item : items) {
            if (remainingToRelease.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal currentReserved = item.getReservedQuantity() != null ? item.getReservedQuantity() : BigDecimal.ZERO;
            if (currentReserved.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal releasedFromItem = currentReserved.min(remainingToRelease);
            item.setReservedQuantity(currentReserved.subtract(releasedFromItem));
            remainingToRelease = remainingToRelease.subtract(releasedFromItem);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductionPlanSummaryResponse> getAllProductionPlans(
            ProductionPlanStatus status, Pageable pageable) {
        
        UserContext ctx = SecurityUtils.getCurrentUserContext();
        if (ctx == null) throw new AccessDeniedException("Unauthorized");
        
        User currentUser = userRepository.findById(ctx.getUserId())
                .orElseThrow(() -> new AccessDeniedException("User not found"));

        Specification<ProductionPlan> spec = 
                Specification.where(
                        ProductionPlanSpecification.hasStatus(status)
                );

        // Security Scope: STAFF only see their kitchen
        boolean isSystemScope = "SYSTEM".equalsIgnoreCase(ctx.getScope());

        if (!isSystemScope) {
            if (currentUser.getKitchen() == null) {
                return Page.empty(pageable);
            }
            spec = spec.and(ProductionPlanSpecification.hasKitchenId(
                    currentUser.getKitchen().getKitchenId()));
        }

        return productionPlanRepository.findAll(spec, pageable)
                .map(this::mapToSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductionPlanDetailResponse getProductionPlanDetail(Long planId) {
        UserContext ctx = SecurityUtils.getCurrentUserContext();
        if (ctx == null) throw new AccessDeniedException("Unauthorized");
        
        User currentUser = userRepository.findById(ctx.getUserId())
                .orElseThrow(() -> new AccessDeniedException("User not found"));

        // Optimized fetch with JOIN FETCH
        ProductionPlan plan = productionPlanRepository.findByIdWithMaterials(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Production Plan not found with id: " + planId));

        // Security Scope: 404 Not Found if mismatch kitchen for STAFF
        boolean isSystemScope = "SYSTEM".equalsIgnoreCase(ctx.getScope());

        if (!isSystemScope) {
            if (currentUser.getKitchen() == null || 
                !currentUser.getKitchen().getKitchenId().equals(plan.getKitchen().getKitchenId())) {
                throw new ResourceNotFoundException("Production Plan not found with id: " + planId);
            }
        }

        return ProductionPlanDetailResponse.builder()
                .planId(plan.getPlanId())
                .planName(plan.getPlanName())
                .batchCode(plan.getBatchCode())
                .kitchenId(plan.getKitchen().getKitchenId())
                .status(plan.getStatus().name())
                .createdAt(plan.getCreatedAt())
                .coordinatorUserId(plan.getCoordinatorUser().getUserId())
                .materials(plan.getMaterialRequirements().stream()
                        .map(req -> MaterialRequirementResponse.builder()
                                .materialId(req.getMaterial().getId())
                                .materialName(req.getMaterial().getName())
                                .requiredQuantity(req.getRequiredQuantity())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    private ProductionPlanSummaryResponse mapToSummaryResponse(ProductionPlan plan) {
        return ProductionPlanSummaryResponse.builder()
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
