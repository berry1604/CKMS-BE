package com.swp.ckms.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class RecipeRequest {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Yield is required")
    @Positive(message = "Yield must be greater than zero")
    private BigDecimal yield;

    @NotBlank(message = "Instructions are required")
    private String instructions;

    @NotEmpty(message = "Recipe details are required")
    @Valid
    private List<RecipeDetailRequest> recipeDetails;
}
