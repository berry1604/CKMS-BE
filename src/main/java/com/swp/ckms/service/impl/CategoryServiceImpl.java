package com.swp.ckms.service.impl;

import com.swp.ckms.dto.request.CategoryRequest;
import com.swp.ckms.dto.response.CategoryResponse;
import com.swp.ckms.entity.Category;
import com.swp.ckms.entity.User;
import com.swp.ckms.exception.business.DuplicateNameException;
import com.swp.ckms.exception.business.ResourceNotFoundException;
import com.swp.ckms.repository.CategoryRepository;
import com.swp.ckms.repository.UserRepository;
import com.swp.ckms.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    @Override
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findByIsActiveTrue().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @SuppressWarnings("null")
    public CategoryResponse createCategory(CategoryRequest request, String username) {
        if (categoryRepository.existsByName(request.getName())) {
            throw new DuplicateNameException("Category with name '" + request.getName() + "' already exists");
        }

        User user = null;
        if (username != null) {
            user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        }

        Category category = Category.builder()
                .name(request.getName())
                .description(request.getDescription())
                .createdByUser(user)
                .isActive(true)
                .build();

        return mapToResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    @SuppressWarnings("null")
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        if (categoryRepository.existsByNameAndIdNot(request.getName(), id)) {
            throw new DuplicateNameException("Category with name '" + request.getName() + "' already exists");
        }

        category.setName(request.getName());
        category.setDescription(request.getDescription());

        return mapToResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        // TODO: Check if category is used by any active products
        // if (productRepository.existsByCategoryIdAndIsActiveTrue(id)) {
        //     throw new CategoryInUseException("Cannot delete category as it is being used by active products");
        // }

        category.setIsActive(false);
        categoryRepository.save(category);
    }

    @SuppressWarnings("null")
    private CategoryResponse mapToResponse(Category category) {
        Long createdByUserId = category.getCreatedByUser() != null ? category.getCreatedByUser().getUserId() : null;
        String createdByUserName = category.getCreatedByUser() != null ? category.getCreatedByUser().getUsername() : null;

        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .createdByUserId(createdByUserId)
                .createdByUserName(createdByUserName)
                .isActive(category.getIsActive())
                .build();
    }
}
