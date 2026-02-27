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
}
