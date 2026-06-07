package com.flashsale.productservice.repository;

import com.flashsale.productservice.entity.ProductVariant;
import com.flashsale.productservice.entity.VariantStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    Optional<ProductVariant> findByVariantCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM ProductVariant v WHERE v.variantCode = :code AND v.deletedAt IS NULL")
    Optional<ProductVariant> findByVariantCodeWithPessimisticLock(@Param("code") String code);

    List<ProductVariant> findByProductIdAndDeletedAtIsNull(UUID productId);

    @Modifying
    @Query("UPDATE ProductVariant v SET v.stockQuantity = v.stockQuantity + :delta WHERE v.id = :variantId")
    int incrementStock(@Param("variantId") UUID variantId, @Param("delta") int delta);

    @Query("SELECT SUM(v.stockQuantity) FROM ProductVariant v WHERE v.productId = :productId AND v.deletedAt IS NULL")
    Integer getTotalStockByProductId(@Param("productId") UUID productId);

    @Query("SELECT COUNT(v) FROM ProductVariant v WHERE v.productId = :productId AND v.deletedAt IS NULL")
    Integer countByProductId(@Param("productId") UUID productId);

    List<ProductVariant> findByProductIdAndStatus(UUID productId, VariantStatus status);

    @Query("SELECT v FROM ProductVariant v WHERE v.productId = :productId AND v.status = 'OUT_OF_STOCK' AND v.deletedAt IS NULL")
    List<ProductVariant> findOutOfStockByProductId(@Param("productId") UUID productId);

    @Query("SELECT v FROM ProductVariant v WHERE v.originalPrice IS NOT NULL AND v.deletedAt IS NULL")
    List<ProductVariant> findByOriginalPriceNotNull();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM ProductVariant v WHERE v.id = :id AND v.deletedAt IS NULL")
    Optional<ProductVariant> findByIdWithPessimisticLock(@Param("id") UUID id);

    /**
     * Returns variants belonging to marketplace-visible, non-deleted products.
     * Used by search-service's reindex pipeline to build the ES catalog
     * (one SKU = one doc).
     */
    @Query("SELECT v FROM ProductVariant v " +
           "WHERE v.deletedAt IS NULL " +
           "  AND v.productId IN (SELECT p.id FROM Product p " +
           "                       WHERE p.status IN ('ACTIVE', 'OUT_OF_STOCK') " +
           "                         AND p.deletedAt IS NULL)")
    Page<ProductVariant> findVariantsOfSearchVisibleProducts(Pageable pageable);
}
