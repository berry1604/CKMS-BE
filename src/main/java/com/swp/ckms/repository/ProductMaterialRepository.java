package com.swp.ckms.repository;

import com.swp.ckms.entity.Product;
import com.swp.ckms.entity.ProductMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductMaterialRepository extends JpaRepository<ProductMaterial, Long> {
    
    List<ProductMaterial> findByProduct(Product product);
    
    Optional<ProductMaterial> findByProductIdAndMaterialId(Long productId, Long materialId);
    
    void deleteByProductIdAndMaterialId(Long productId, Long materialId);
}
