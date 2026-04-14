package com.flashsale.productdomain.domain.repository;

import com.flashsale.productdomain.domain.model.ProductVariant;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends MongoRepository<ProductVariant, String> {
    Optional<ProductVariant> findBySkuCode(String skuCode);

    List<ProductVariant> findByProductId(String productId);

    boolean existsBySkuCode(String skuCode);
}

