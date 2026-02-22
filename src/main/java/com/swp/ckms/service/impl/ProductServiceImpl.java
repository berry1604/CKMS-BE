package com.swp.ckms.service.impl;

import com.swp.ckms.dto.request.ProductRequest;
import com.swp.ckms.dto.response.ProductResponse;
import com.swp.ckms.entity.Category;
import com.swp.ckms.entity.Product;
import com.swp.ckms.exception.business.DuplicateNameException;
import com.swp.ckms.exception.business.ResourceNotFoundException;
import com.swp.ckms.repository.CategoryRepository;
import com.swp.ckms.repository.MaterialRepository;
import com.swp.ckms.repository.ProductRepository;
import com.swp.ckms.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final MaterialRepository materialRepository;

    @Override
    public Page<ProductResponse> getAllProducts(String search, Long categoryId, Pageable pageable) {
        Page<Product> products;
        
        if (search != null && !search.trim().isEmpty()) {
            products = productRepository.findByNameContainingIgnoreCaseAndIsActiveTrue(search, pageable);
        } else if (categoryId != null) {
            products = productRepository.findByCategoryIdAndIsActiveTrue(categoryId, pageable);
        } else {
            products = productRepository.findByIsActiveTrue(pageable);
        }
        
        return products.map(this::mapToResponse);
    }

    @Override
    public ProductResponse getProductById(Long id) {
        Product product = findProductById(id);
        return mapToResponse(product);
    }

    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        if (productRepository.existsByName(request.getName())) {
            throw new DuplicateNameException("Product name already exists");
        }

        Category category = findCategoryById(request.getCategoryId());
        
        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .category(category)
                .isActive(true)
                .build();
        
        product = productRepository.save(product);
        return mapToResponse(product);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = findProductById(id);
        
        // Check duplicate name if name changed
        if (!product.getName().equals(request.getName()) && productRepository.existsByName(request.getName())) {
            throw new DuplicateNameException("Product name already exists");
        }
        
        Category category = findCategoryById(request.getCategoryId());
        
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCategory(category);
        
        product = productRepository.save(product);
        return mapToResponse(product);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        Product product = findProductById(id);
        product.setIsActive(false);
        productRepository.save(product);
    }


    private Product findProductById(Long id) {
        return productRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }
    
    private Category findCategoryById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        if (!category.getIsActive()) {
            throw new IllegalArgumentException("Category is not active");
        }
        return category;
    }
    
    private ProductResponse mapToResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .isActive(product.getIsActive())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
