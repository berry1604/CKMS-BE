package com.swp.ckms.controller;

import com.swp.ckms.dto.request.MaterialRequest;
import com.swp.ckms.dto.response.ApiResponse;
import com.swp.ckms.dto.response.MaterialResponse;
import com.swp.ckms.service.MaterialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/materials")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;

    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_MATERIAL')")
    public ResponseEntity<ApiResponse<List<MaterialResponse>>> getAllMaterials() {
        return ResponseEntity.ok(ApiResponse.<List<MaterialResponse>>builder()
                .status(HttpStatus.OK.value())
                .message("Materials retrieved successfully")
                .data(materialService.getAllMaterials())
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MANAGE_CATALOG')")
    public ResponseEntity<ApiResponse<MaterialResponse>> createMaterial(@Valid @RequestBody MaterialRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<MaterialResponse>builder()
                .status(HttpStatus.CREATED.value())
                .message("Material created successfully")
                .data(materialService.createMaterial(request))
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_CATALOG')")
    public ResponseEntity<ApiResponse<MaterialResponse>> updateMaterial(
            @PathVariable Long id,
            @Valid @RequestBody MaterialRequest request) {
        return ResponseEntity.ok(ApiResponse.<MaterialResponse>builder()
                .status(HttpStatus.OK.value())
                .message("Material updated successfully")
                .data(materialService.updateMaterial(id, request))
                .timestamp(LocalDateTime.now())
                .build());
    }
}
