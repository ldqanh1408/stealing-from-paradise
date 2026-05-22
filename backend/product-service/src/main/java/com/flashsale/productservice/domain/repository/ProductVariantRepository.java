package com.flashsale.productservice.domain.repository;

import com.flashsale.productservice.domain.model.ProductVariant;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends MongoRepository<ProductVariant, String> {
    
    // Find by variant code (SKU)
    Optional<ProductVariant> findByVariantCode(String variantCode);
    
    // Find multiple by variant codes
    List<ProductVariant> findByVariantCodeIn(List<String> variantCodes);
    
    // Find by product ID
    List<ProductVariant> findByProductId(String productId);
    
    // Check existence
    boolean existsByVariantCode(String variantCode);
    
    // Find active variants by product ID
    List<ProductVariant> findByProductIdAndStatus(String productId, String status);
}
