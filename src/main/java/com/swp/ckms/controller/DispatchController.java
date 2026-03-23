package com.swp.ckms.controller;

import com.swp.ckms.dto.response.ApiResponse;
import com.swp.ckms.dto.response.DispatchSuggestionResponse;
import com.swp.ckms.security.SecurityUtils;
import com.swp.ckms.security.UserContext;
import com.swp.ckms.service.DispatchService;
import com.swp.ckms.repository.CentralKitchenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/dispatch")
@RequiredArgsConstructor
public class DispatchController {

    private final DispatchService dispatchService;
    private final CentralKitchenRepository kitchenRepository;

    @GetMapping("/suggest")
    @PreAuthorize("hasAuthority('CREATE_PRODUCTION_PLAN') or hasAuthority('ORGANIZE_PRODUCTION')")
    public ApiResponse<DispatchSuggestionResponse> getDispatchSuggestion(
            @RequestParam(required = false) Long kitchenId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) {
        
        UserContext ctx = SecurityUtils.getCurrentUserContext();
        Long finalKitchenId = kitchenId;
        
        if (finalKitchenId == null) {
            finalKitchenId = ctx.getCoordinatorId();
        }

        // Smart Fallback: If still null, check if there's only one kitchen in the system
        if (finalKitchenId == null) {
            java.util.List<com.swp.ckms.entity.CentralKitchen> kitchens = kitchenRepository.findAll();
            if (kitchens.size() == 1) {
                finalKitchenId = kitchens.get(0).getKitchenId();
            } else if (kitchens.isEmpty()) {
                throw new com.swp.ckms.exception.business.ResourceNotFoundException("No Central Kitchen found in the system");
            } else {
                throw new IllegalArgumentException("Multiple kitchens found. kitchenId is required to disambiguate.");
            }
        }

        DispatchSuggestionResponse suggestion = dispatchService.suggestProductionPlan(finalKitchenId, targetDate);
        return ApiResponse.success(suggestion);
    }
}
