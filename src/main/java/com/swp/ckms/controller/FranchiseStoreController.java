package com.swp.ckms.controller;

import com.swp.ckms.dto.request.StoreCreateRequest;
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
}
