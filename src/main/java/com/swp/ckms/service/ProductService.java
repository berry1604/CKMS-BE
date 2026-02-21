package com.swp.ckms.service;

import com.swp.ckms.dto.request.AddMaterialRequest;
import com.swp.ckms.dto.request.ProductRequest;
import com.swp.ckms.dto.response.ProductMaterialResponse;
import com.swp.ckms.dto.response.ProductResponse;
import com.swp.ckms.entity.Category;
import com.swp.ckms.entity.Material;
import com.swp.ckms.entity.Product;
import com.swp.ckms.entity.ProductMaterial;
import com.swp.ckms.exception.business.DuplicateNameException;
import com.swp.ckms.exception.business.ResourceNotFoundException;
import com.swp.ckms.repository.CategoryRepository;
import com.swp.ckms.repository.MaterialRepository;
import com.swp.ckms.repository.ProductMaterialRepository;
import com.swp.ckms.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final MaterialRepository materialRepository;
    private final ProductMaterialRepository productMaterialRepository;

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

    public ProductResponse getProductById(Long id) {
        Product product = findProductById(id);
        return mapToResponse(product);
    }

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

    @Transactional
    public void deleteProduct(Long id) {
        Product product = findProductById(id);
        product.setIsActive(false);
        productRepository.save(product);
    }

    @Transactional
    public void addMaterialToProduct(Long productId, AddMaterialRequest request) {
        Product product = findProductById(productId);
        
        Material material = materialRepository.findById(request.getMaterialId())
                .orElseThrow(() -> new ResourceNotFoundException("Material not found"));
        
        if (!material.getIsActive()) {
             throw new IllegalArgumentException("Cannot add inactive material");
        }
        
        if (productMaterialRepository.findByProductIdAndMaterialId(productId, request.getMaterialId()).isPresent()) {
            throw new IllegalArgumentException("Material already added to this product");
        }
        
        // Use default unit if not provided
        String unit = request.getUnit() != null ? request.getUnit() : material.getUnit().name();
        
        ProductMaterial productMaterial = ProductMaterial.builder()
                .product(product)
                .material(material)
                .quantity(request.getQuantity())
                .unit(unit)
                .build();
        
        productMaterialRepository.save(productMaterial);
    }

    @Transactional
    public void removeMaterialFromProduct(Long productId, Long materialId) {
        if (!productMaterialRepository.findByProductIdAndMaterialId(productId, materialId).isPresent()) {
             throw new ResourceNotFoundException("Material not found in this product");
        }
        productMaterialRepository.deleteByProductIdAndMaterialId(productId, materialId);
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
        List<ProductMaterial> materials = productMaterialRepository.findByProduct(product);
        
        List<ProductMaterialResponse> materialResponses = materials.stream()
                .map(this::mapToMaterialResponse)
                .collect(Collectors.toList());
                
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
                .materials(materialResponses)
                .build();
    }
    
    private ProductMaterialResponse mapToMaterialResponse(ProductMaterial pm) {
        return ProductMaterialResponse.builder()
                .materialId(pm.getMaterial().getId())
                .materialName(pm.getMaterial().getName())
                .quantity(pm.getQuantity())
                .unit(pm.getUnit())
                .build();
    }
}
