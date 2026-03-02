package com.swp.ckms.service;

import com.swp.ckms.dto.request.RecipeRequest;
import com.swp.ckms.dto.response.RecipeResponse;

public interface RecipeService {
    RecipeResponse createRecipe(RecipeRequest request, String username);
    RecipeResponse getActiveRecipeByProductId(Long productId);
    RecipeResponse toggleRecipeStatus(Long recipeId, boolean status);
}
