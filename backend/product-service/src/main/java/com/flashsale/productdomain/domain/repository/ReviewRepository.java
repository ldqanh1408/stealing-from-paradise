package com.flashsale.productdomain.domain.repository;

import com.flashsale.productdomain.domain.model.Review;
import com.flashsale.productdomain.domain.model.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    @Query("""
        select distinct r from Review r
        left join r.media m
        where r.product.id = :productId
          and r.status = :status
          and (:rating is null or r.rating = :rating)
          and (:hasMedia = false or m.id is not null)
        """)
    Page<Review> findByFilters(
            @Param("productId") UUID productId,
            @Param("status") ReviewStatus status,
            @Param("rating") Short rating,
            @Param("hasMedia") boolean hasMedia,
            Pageable pageable);
}
