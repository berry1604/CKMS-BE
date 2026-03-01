package com.swp.ckms.controller;

import com.swp.ckms.dto.request.CategoryRequest;
import com.swp.ckms.dto.response.ApiResponse;
import com.swp.ckms.dto.response.CategoryResponse;
import com.swp.ckms.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @PreAuthorize("hasAuthority('VIEW_CATEGORY')")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAllCategories() {
        return ResponseEntity.ok(ApiResponse.<List<CategoryResponse>>builder()
                .status(HttpStatus.OK.value())
                .message("Categories retrieved successfully")
                .data(categoryService.getAllCategories())
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MANAGE_CATALOG')") 
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody CategoryRequest request,
            java.security.Principal principal) {
            
        String username = principal != null ? principal.getName() : null;
        
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<CategoryResponse>builder()
                .status(HttpStatus.CREATED.value())
                .message("Category created successfully")
                .data(categoryService.createCategory(request, username))
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_CATALOG')")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.<CategoryResponse>builder()
                .status(HttpStatus.OK.value())
                .message("Category updated successfully")
                .data(categoryService.updateCategory(id, request))
                .timestamp(LocalDateTime.now())
                .build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_CATALOG')")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Category deleted successfully")
                .timestamp(LocalDateTime.now())
                .build());
    }
}
