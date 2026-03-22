package com.swp.ckms.service.impl;

import com.swp.ckms.dto.request.KitchenCreateRequest;
import com.swp.ckms.dto.request.KitchenUpdateRequest;
import com.swp.ckms.dto.response.KitchenResponse;
import com.swp.ckms.entity.CentralKitchen;
import com.swp.ckms.entity.KitchenWarehouse;
import com.swp.ckms.entity.User;
import com.swp.ckms.exception.business.ResourceNotFoundException;
import com.swp.ckms.repository.CentralKitchenRepository;
import com.swp.ckms.repository.KitchenWarehouseRepository;
import com.swp.ckms.repository.UserRepository;
import com.swp.ckms.security.SecurityUtils;
import com.swp.ckms.security.UserContext;
import com.swp.ckms.service.KitchenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KitchenServiceImpl implements KitchenService {

    private final CentralKitchenRepository kitchenRepository;
    private final UserRepository userRepository;
    private final KitchenWarehouseRepository warehouseRepository;
    private final com.swp.ckms.repository.ProductionPlanRepository productionPlanRepository;

    @Override
    @Transactional
    public KitchenResponse createKitchen(KitchenCreateRequest request) {
        CentralKitchen kitchen = CentralKitchen.builder()
                .name(request.getName())
                .address(request.getAddress())
                .maxDailyCapacity(request.getMaxDailyCapacity())
                .build();

        CentralKitchen savedKitchen = kitchenRepository.save(kitchen);

        // Auto-create default warehouse for this kitchen
        KitchenWarehouse warehouse = KitchenWarehouse.builder()
                .kitchen(savedKitchen)
                .name("Kho - " + savedKitchen.getName())
                .maxCapacity(new java.math.BigDecimal("1000")) // Default max capacity
                .build();
        warehouseRepository.save(warehouse);

        return mapToResponse(savedKitchen);
    }

    @Override
    @Transactional
    public KitchenResponse updateKitchen(Long kitchenId, KitchenUpdateRequest request) {
        CentralKitchen kitchen = kitchenRepository.findById(kitchenId)
                .orElseThrow(() -> new ResourceNotFoundException("Kitchen not found with ID: " + kitchenId));

        validateKitchenAccess(kitchenId);

        if (request.getName() != null) kitchen.setName(request.getName());
        if (request.getAddress() != null) kitchen.setAddress(request.getAddress());
        if (request.getMaxDailyCapacity() != null) kitchen.setMaxDailyCapacity(request.getMaxDailyCapacity());

        return mapToResponse(kitchenRepository.save(kitchen));
    }

    @Override
    @Transactional(readOnly = true)
    public KitchenResponse getKitchenById(Long kitchenId) {
        CentralKitchen kitchen = kitchenRepository.findById(kitchenId)
                .orElseThrow(() -> new ResourceNotFoundException("Kitchen not found with ID: " + kitchenId));
        return mapToResponse(kitchen);
    }

    @Override
    @Transactional(readOnly = true)
    public List<KitchenResponse> getAllKitchens() {
        return kitchenRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteKitchen(Long kitchenId) {
        CentralKitchen kitchen = kitchenRepository.findById(kitchenId)
                .orElseThrow(() -> new ResourceNotFoundException("Kitchen not found with ID: " + kitchenId));
        
        // Check if there are users or other dependencies if needed
        // For now, strict delete
        kitchenRepository.delete(kitchen);
    }

    private void validateKitchenAccess(Long kitchenId) {
        UserContext ctx = SecurityUtils.getCurrentUserContext();
        if (ctx == null) throw new AccessDeniedException("Unauthorized");

        // Admins with SYSTEM scope can update any kitchen
        if ("SYSTEM".equalsIgnoreCase(ctx.getScope())) {
             return;
        }

        // Managers can only update their own kitchen
        User currentUser = userRepository.findById(ctx.getUserId())
                .orElseThrow(() -> new AccessDeniedException("User not found"));

        if (currentUser.getKitchen() == null || !currentUser.getKitchen().getKitchenId().equals(kitchenId)) {
            throw new AccessDeniedException("You do not have permission to manage this kitchen");
        }
    }

    private KitchenResponse mapToResponse(CentralKitchen kitchen) {
        Long kitchenId = kitchen.getKitchenId();

        Long warehouseId = warehouseRepository.findByKitchen_KitchenId(kitchenId)
                .stream()
                .map(KitchenWarehouse::getWarehouseId)
                .findFirst()
                .orElse(null);

        // 1. currentStatus: check if any plan is IN_PRODUCTION
        boolean isProducing = productionPlanRepository.existsByKitchen_KitchenIdAndStatus(
                kitchenId, com.swp.ckms.enums.ProductionPlanStatus.IN_PRODUCTION);
        String currentStatus = isProducing ? "IN_PRODUCTION" : "IDLE";

        // 2. activePlanCount: count plans that are active (any date, matching statuses)
        java.util.List<com.swp.ckms.enums.ProductionPlanStatus> activeStatuses = java.util.List.of(
                com.swp.ckms.enums.ProductionPlanStatus.PLANNED,
                com.swp.ckms.enums.ProductionPlanStatus.READY_TO_PRODUCE,
                com.swp.ckms.enums.ProductionPlanStatus.IN_PRODUCTION);

        long activePlanCount = productionPlanRepository.countByKitchen_KitchenIdAndStatusIn(
                kitchenId, activeStatuses);

        // 3. todayUsedCapacity: sum of planned quantities for active plans
        java.math.BigDecimal todayUsedCapacity = productionPlanRepository
                .sumPlannedQuantityByKitchenAndStatuses(kitchenId, activeStatuses);
        if (todayUsedCapacity == null) todayUsedCapacity = java.math.BigDecimal.ZERO;

        return KitchenResponse.builder()
                .kitchenId(kitchenId)
                .name(kitchen.getName())
                .address(kitchen.getAddress())
                .maxDailyCapacity(kitchen.getMaxDailyCapacity())
                .warehouseId(warehouseId)
                .currentStatus(currentStatus)
                .activePlanCount((int) activePlanCount)
                .todayUsedCapacity(todayUsedCapacity)
                .build();
    }
}
