package com.swp.ckms.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class RecipeResponse {
    private Long recipeId;
    private Long productId;
    private String productName;
    private Long createdByUserId;
    private String createdByUserName;
    private Integer version;
    private Boolean isActive;
    private BigDecimal yield;
    private String instructions;
    private List<RecipeDetailResponse> recipeDetails;
}
