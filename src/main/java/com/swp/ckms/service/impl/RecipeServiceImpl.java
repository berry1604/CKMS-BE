package com.swp.ckms.service.impl;

import com.swp.ckms.dto.request.RecipeDetailRequest;
import com.swp.ckms.dto.request.RecipeRequest;
import com.swp.ckms.dto.response.RecipeDetailResponse;
import com.swp.ckms.dto.response.RecipeResponse;
import com.swp.ckms.entity.Material;
import com.swp.ckms.entity.Product;
import com.swp.ckms.entity.Recipe;
import com.swp.ckms.entity.RecipeDetail;
import com.swp.ckms.entity.User;
import com.swp.ckms.exception.business.DuplicateResourceException;
import com.swp.ckms.exception.business.ResourceNotFoundException;
import com.swp.ckms.repository.MaterialRepository;
import com.swp.ckms.repository.ProductRepository;
import com.swp.ckms.repository.RecipeDetailRepository;
import com.swp.ckms.repository.RecipeRepository;
import com.swp.ckms.repository.UserRepository;
import com.swp.ckms.service.RecipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecipeServiceImpl implements RecipeService {

    private final RecipeRepository recipeRepository;
    private final RecipeDetailRepository recipeDetailRepository;
    private final ProductRepository productRepository;
    private final MaterialRepository materialRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public RecipeResponse createRecipe(RecipeRequest request, String username) {
        Product product = productRepository.findByIdAndIsActiveTrue(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found or inactive"));

        if (recipeRepository.existsByProductIdAndIsActiveTrue(request.getProductId())) {
            throw new DuplicateResourceException("An active recipe already exists for this product.");
        }

        User user = null;
        if (username != null) {
            user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        }

        Recipe recipe = Recipe.builder()
                .product(product)
                .createdByUser(user)
                .version(1)
                .isActive(true)
                .yield(request.getYield())
                .instructions(request.getInstructions())
                .recipeDetails(new ArrayList<>())
                .build();

        recipe = recipeRepository.save(recipe);

        for (RecipeDetailRequest detailRequest : request.getRecipeDetails()) {
            Material material = materialRepository.findById(detailRequest.getMaterialId())
                    .orElseThrow(() -> new ResourceNotFoundException("Material ID " + detailRequest.getMaterialId() + " not found"));

            if (!material.getIsActive()) {
                 throw new IllegalArgumentException("Cannot use inactive material ID " + material.getId());
            }

            RecipeDetail recipeDetail = RecipeDetail.builder()
                    .recipe(recipe)
                    .material(material)
                    .quantityNeeded(detailRequest.getQuantityNeeded())
                    .build();

            recipe.getRecipeDetails().add(recipeDetailRepository.save(recipeDetail));
        }

        return mapToResponse(recipe);
    }

    @Override
    @Transactional(readOnly = true)
    public RecipeResponse getActiveRecipeByProductId(Long productId) {
        Recipe recipe = recipeRepository.findByProductIdAndIsActiveTrue(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Active recipe not found for product ID: " + productId));
        return mapToResponse(recipe);
    }

    @Override
    @Transactional
    public RecipeResponse toggleRecipeStatus(Long recipeId, boolean status) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found with id: " + recipeId));

        if (status) { // Reactivation check
            // BR-04: Cannot activate if any constituent material is inactive
            for (RecipeDetail detail : recipe.getRecipeDetails()) {
                if (detail.getMaterial() == null || !detail.getMaterial().getIsActive()) {
                    String matName = detail.getMaterial() != null ? detail.getMaterial().getName() : "Unknown";
                    throw new com.swp.ckms.exception.business.BusinessRuleViolationException(
                            "Cannot activate recipe because material '" + matName + "' is inactive."
                    );
                }
            }
            
            // If activating strictly for a product, ensure only one is active
            if (recipeRepository.existsByProductIdAndIsActiveTrue(recipe.getProduct().getId()) && !recipe.getIsActive()) {
                 throw new DuplicateResourceException("Another active recipe already exists for this product. Deactivate it first.");
            }
        }

        recipe.setIsActive(status);
        return mapToResponse(recipeRepository.save(recipe));
    }

    private RecipeResponse mapToResponse(Recipe recipe) {
        List<RecipeDetailResponse> detailResponses = recipe.getRecipeDetails().stream()
                .map(detail -> RecipeDetailResponse.builder()
                        .id(detail.getId())
                        .materialId(detail.getMaterial().getId())
                        .materialName(detail.getMaterial().getName())
                        .materialUnit(String.valueOf(detail.getMaterial().getUnit()))
                        .quantityNeeded(detail.getQuantityNeeded())
                        .build())
                .collect(Collectors.toList());

        String createdByUserName = recipe.getCreatedByUser() != null ? recipe.getCreatedByUser().getUsername() : null;
        Long createdByUserId = recipe.getCreatedByUser() != null ? recipe.getCreatedByUser().getUserId() : null;

        return RecipeResponse.builder()
                .recipeId(recipe.getRecipeId())
                .productId(recipe.getProduct().getId())
                .productName(recipe.getProduct().getName())
                .createdByUserId(createdByUserId)
                .createdByUserName(createdByUserName)
                .version(recipe.getVersion())
                .isActive(recipe.getIsActive())
                .yield(recipe.getYield())
                .instructions(recipe.getInstructions())
                .recipeDetails(detailResponses)
                .build();
    }
}
