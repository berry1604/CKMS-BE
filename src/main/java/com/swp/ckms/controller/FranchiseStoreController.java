package com.swp.ckms.controller;

import com.swp.ckms.dto.request.StoreCreateRequest;
import com.swp.ckms.dto.request.StoreUpdateRequest;
import com.swp.ckms.dto.response.StoreResponse;
import com.swp.ckms.service.FranchiseStoreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.swp.ckms.dto.response.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
public class FranchiseStoreController {

    private final FranchiseStoreService storeService;

    @PostMapping
    @PreAuthorize("hasAuthority('MANAGE_STORES')")
    public ResponseEntity<StoreResponse> createStore(@Valid @RequestBody StoreCreateRequest request) {
        StoreResponse response = storeService.createStore(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_STORES')")
    public ResponseEntity<ApiResponse<StoreResponse>> getStore(
            @PathVariable Long id
    ) {

        StoreResponse response = storeService.getStoreById(id);

        return ResponseEntity.ok(
                ApiResponse.success("Store fetched successfully", response)
        );
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MANAGE_STORES')")
    public ResponseEntity<ApiResponse<Page<StoreResponse>>> getAllStores(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search
    ) {

        Page<StoreResponse> response =
                storeService.getAllStores(page, size, search);

        return ResponseEntity.ok(
                ApiResponse.success("Stores fetched successfully", response)
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_STORES')")
    public ResponseEntity<ApiResponse<StoreResponse>> updateStore(
            @PathVariable Long id,
            @Valid @RequestBody StoreUpdateRequest request
    ) {

        StoreResponse response = storeService.updateStore(id, request);

        return ResponseEntity.ok(
                ApiResponse.success("Store updated successfully", response)
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_STORES')")
    public ResponseEntity<ApiResponse<String>> deleteStore(
            @PathVariable Long id
    ) {

        storeService.deleteStore(id);

        return ResponseEntity.ok(
                ApiResponse.success("Store deleted successfully", null)
        );
    }
}
