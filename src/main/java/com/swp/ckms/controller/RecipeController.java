package com.swp.ckms.controller;

import com.swp.ckms.dto.request.RecipeRequest;
import com.swp.ckms.dto.response.ApiResponse;
import com.swp.ckms.dto.response.RecipeResponse;
import com.swp.ckms.service.RecipeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RecipeResponse>> createRecipe(
            @Valid @RequestBody RecipeRequest request,
            Principal principal) {
        
        String username = principal != null ? principal.getName() : null;
        RecipeResponse response = recipeService.createRecipe(request, username);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<RecipeResponse>builder()
                .status(HttpStatus.CREATED.value())
                .message("Recipe created successfully")
                .data(response)
                .timestamp(LocalDateTime.now())
                .build());
    }

    @GetMapping("/product/{productId}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RecipeResponse>> getActiveRecipe(@PathVariable Long productId) {
        RecipeResponse response = recipeService.getActiveRecipeByProductId(productId);
        
        return ResponseEntity.ok(ApiResponse.<RecipeResponse>builder()
                .status(HttpStatus.OK.value())
                .message("Active recipe retrieved successfully")
                .data(response)
                .timestamp(LocalDateTime.now())
                .build());
    }
}
