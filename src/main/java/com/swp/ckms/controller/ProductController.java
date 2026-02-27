package com.swp.ckms.controller;

import com.swp.ckms.dto.request.ProductRequest;
import com.swp.ckms.dto.response.ApiResponse;
import com.swp.ckms.dto.response.ProductResponse;
import com.swp.ckms.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @PreAuthorize("hasAnyRole('STORE_STAFF', 'KITCHEN_STAFF', 'COORDINATOR', 'ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> getAllProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @PageableDefault(sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        
        return ResponseEntity.ok(ApiResponse.<Page<ProductResponse>>builder()
                .status(HttpStatus.OK.value())
                .message("Products retrieved successfully")
                .data(productService.getAllProducts(search, categoryId, pageable))
                .timestamp(LocalDateTime.now())
                .build());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STORE_STAFF', 'KITCHEN_STAFF', 'COORDINATOR', 'ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.<ProductResponse>builder()
                .status(HttpStatus.OK.value())
                .message("Product retrieved successfully")
                .data(productService.getProductById(id))
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<ProductResponse>builder()
                .status(HttpStatus.CREATED.value())
                .message("Product created successfully")
                .data(productService.createProduct(request))
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(ApiResponse.<ProductResponse>builder()
                .status(HttpStatus.OK.value())
                .message("Product updated successfully")
                .data(productService.updateProduct(id, request))
                .timestamp(LocalDateTime.now())
                .build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Product deleted successfully")
                .timestamp(LocalDateTime.now())
                .build());
    }

}
