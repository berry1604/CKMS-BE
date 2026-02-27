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
        
        // Checking Coordinator / Admin logic: either user is Admin or User's kitchen == Plan's kitchen
        boolean isAdmin = currentUser.getRole() != null && "ADMIN".equalsIgnoreCase(currentUser.getRole().getRoleName());
        if (!isAdmin) {
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
}
