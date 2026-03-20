package com.swp.ckms.controller;

import com.swp.ckms.dto.request.KitchenCreateRequest;
import com.swp.ckms.dto.request.KitchenUpdateRequest;
import com.swp.ckms.dto.response.ApiResponse;
import com.swp.ckms.dto.response.KitchenResponse;
import com.swp.ckms.service.KitchenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/kitchens")
@RequiredArgsConstructor
public class KitchenController {

    private final KitchenService kitchenService;

    @PostMapping
    @PreAuthorize("hasAuthority('MANAGE_KITCHEN_CONFIG')")
    public ApiResponse<KitchenResponse> createKitchen(@RequestBody @Valid KitchenCreateRequest request) {
        return ApiResponse.success(kitchenService.createKitchen(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_KITCHEN') or hasAuthority('MANAGE_KITCHEN_CONFIG')")
    public ApiResponse<List<KitchenResponse>> getAllKitchens() {
        return ApiResponse.success(kitchenService.getAllKitchens());
    }

    @GetMapping("/{kitchenId}")
    @PreAuthorize("hasAuthority('VIEW_KITCHEN') or hasAuthority('MANAGE_KITCHEN_CONFIG')")
    public ApiResponse<KitchenResponse> getKitchenById(@PathVariable Long kitchenId) {
        return ApiResponse.success(kitchenService.getKitchenById(kitchenId));
    }

    @PatchMapping("/{kitchenId}")
    @PreAuthorize("hasAuthority('MANAGE_KITCHEN_CONFIG')")
    public ApiResponse<KitchenResponse> updateKitchen(
            @PathVariable Long kitchenId,
            @RequestBody @Valid KitchenUpdateRequest request) {
        return ApiResponse.success(kitchenService.updateKitchen(kitchenId, request));
    }

    @DeleteMapping("/{kitchenId}")
    @PreAuthorize("hasAuthority('MANAGE_KITCHEN_CONFIG')")
    public ApiResponse<Void> deleteKitchen(@PathVariable Long kitchenId) {
        kitchenService.deleteKitchen(kitchenId);
        return ApiResponse.success(null);
    }
}
