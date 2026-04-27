package com.flashsale.productdomain.domain.repository;

import com.flashsale.productdomain.domain.model.ReviewSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReviewSummaryRepository extends JpaRepository<ReviewSummary, UUID> {
    Optional<ReviewSummary> findByProductId(UUID productId);
}
