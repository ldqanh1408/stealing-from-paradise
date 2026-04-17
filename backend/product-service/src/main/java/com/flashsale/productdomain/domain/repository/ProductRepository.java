package com.flashsale.productdomain.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import com.flashsale.productdomain.domain.model.Product;

import java.util.Optional;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

    Optional<Product> findByIdAndSellerId(String id, Long sellerId);

    Page<Product> findBySellerIdAndDeletedAtIsNull(Long sellerId, Pageable pageable);

    Page<Product> findByStatusAndDeletedAtIsNull(String status, Pageable pageable);

    Page<Product> findByStatusAndCategoryIdAndDeletedAtIsNull(String status, String categoryId, Pageable pageable);

    Page<Product> findByStatusAndSellerIdAndDeletedAtIsNull(String status, Long sellerId, Pageable pageable);

    long countBySellerIdAndStatusAndDeletedAtIsNull(Long sellerId, String status);

    Optional<Product> findByIdAndDeletedAtIsNull(String id);
}
