package com.flashsale.productdomain.domain.repository;

import com.flashsale.productdomain.domain.model.Product;
import com.flashsale.productdomain.domain.model.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findBySlugAndStatusIn(String slug, Collection<ProductStatus> statuses);

    Optional<Product> findByIdAndSellerId(UUID id, Long sellerId);
}
