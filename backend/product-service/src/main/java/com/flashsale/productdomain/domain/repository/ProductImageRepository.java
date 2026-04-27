package com.flashsale.productdomain.domain.repository;

import com.flashsale.productdomain.domain.model.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {
    List<ProductImage> findByProductIdOrderBySortOrderAsc(UUID productId);

    List<ProductImage> findByProductIdAndSkuId(UUID productId, UUID skuId);

    List<ProductImage> findByProductIdAndSkuIsNullOrderBySortOrderAsc(UUID productId);
}
