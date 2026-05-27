package com.flashsale.productservice.repository;

import com.flashsale.productservice.entity.Product;
import com.flashsale.productservice.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findBySlug(String slug);

    Page<Product> findBySellerIdAndDeletedAtIsNull(Long sellerId, Pageable pageable);

    Page<Product> findByCategoryIdAndDeletedAtIsNull(UUID categoryId, Pageable pageable);

    Page<Product> findByStatusAndDeletedAtIsNull(ProductStatus status, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status = :status AND p.deletedAt IS NULL")
    Page<Product> findByStatus(@Param("status") ProductStatus status, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status = 'PENDING' AND p.deletedAt IS NULL " +
           "AND (:categoryId IS NULL OR p.categoryId = :categoryId) " +
           "AND (:sellerId IS NULL OR p.sellerId = :sellerId)")
    Page<Product> findPendingProducts(
            @Param("categoryId") UUID categoryId,
            @Param("sellerId") Long sellerId,
            Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status IN :statuses AND p.deletedAt IS NULL")
    Page<Product> findByStatusIn(@Param("statuses") java.util.List<ProductStatus> statuses, Pageable pageable);
}
