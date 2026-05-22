package com.flashsale.productservice.domain.repository;

import com.flashsale.productservice.domain.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
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

    @Query("{ 'status': 'ACTIVE', 'deletedAt': null, 'name': { $regex: ?0, $options: 'i' } }")
    Page<Product> findActiveByNameContaining(String namePattern, Pageable pageable);

    @Query("{ 'status': 'ACTIVE', 'deletedAt': null, 'categoryId': ?0, 'name': { $regex: ?1, $options: 'i' } }")
    Page<Product> findActiveByCategoryAndNameContaining(String categoryId, String namePattern, Pageable pageable);

    // Deprecated PUBLISHED queries kept for backward compatibility during migration
    @Deprecated
    @Query("{ 'status': 'PUBLISHED', 'deletedAt': null, 'name': { $regex: ?0, $options: 'i' } }")
    Page<Product> findPublishedByNameContaining(String namePattern, Pageable pageable);

    @Deprecated
    @Query("{ 'status': 'PUBLISHED', 'deletedAt': null, 'categoryId': ?0, 'name': { $regex: ?1, $options: 'i' } }")
    Page<Product> findPublishedByCategoryAndNameContaining(String categoryId, String namePattern, Pageable pageable);
}
