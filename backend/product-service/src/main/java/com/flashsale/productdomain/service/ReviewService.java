package com.flashsale.productdomain.service;

import com.flashsale.productdomain.domain.model.Product;
import com.flashsale.productdomain.domain.model.Review;
import com.flashsale.productdomain.domain.model.ReviewMedia;
import com.flashsale.productdomain.domain.model.ReviewMediaType;
import com.flashsale.productdomain.domain.model.ReviewStatus;
import com.flashsale.productdomain.domain.model.ReviewSummary;
import com.flashsale.productdomain.domain.model.Sku;
import com.flashsale.productdomain.domain.repository.ProductRepository;
import com.flashsale.productdomain.domain.repository.ReviewMediaRepository;
import com.flashsale.productdomain.domain.repository.ReviewRepository;
import com.flashsale.productdomain.domain.repository.ReviewSummaryRepository;
import com.flashsale.productdomain.domain.repository.SkuRepository;
import com.flashsale.productdomain.dto.request.CreateReviewMediaRequest;
import com.flashsale.productdomain.dto.request.CreateReviewRequest;
import com.flashsale.productdomain.dto.response.ReviewMediaResponse;
import com.flashsale.productdomain.dto.response.ReviewResponse;
import com.flashsale.productdomain.dto.response.ReviewSummaryResponse;
import com.flashsale.productdomain.exception.BusinessRuleException;
import com.flashsale.productdomain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewMediaRepository reviewMediaRepository;
    private final ReviewSummaryRepository reviewSummaryRepository;
    private final ProductRepository productRepository;
    private final SkuRepository skuRepository;

    @Transactional
    public ReviewResponse createReview(UUID productId, Long customerId, CreateReviewRequest request) {
        // Verify product exists
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        // Verify SKU exists if provided
        Sku sku = null;
        if (request.getSkuId() != null) {
            sku = skuRepository.findById(request.getSkuId())
                    .orElseThrow(() -> new ResourceNotFoundException("SKU not found"));
        }

        // Create review
        Review review = Review.builder()
                .product(product)
                .sku(sku)
                .orderItemId(request.getOrderItemId())
                .customerId(customerId)
                .rating(request.getRating().shortValue())
                .title(request.getTitle())
                .content(request.getContent())
                .status(ReviewStatus.APPROVED)
                .media(new ArrayList<>())
                .build();

        review = reviewRepository.save(review);

        // Save media if provided
        if (request.getMedia() != null && !request.getMedia().isEmpty()) {
            for (CreateReviewMediaRequest mediaRequest : request.getMedia()) {
                ReviewMedia media = ReviewMedia.builder()
                        .review(review)
                        .url(mediaRequest.getUrl())
                        .mediaType(mediaRequest.getMediaType() != null
                                ? ReviewMediaType.valueOf(mediaRequest.getMediaType().toUpperCase())
                                : ReviewMediaType.IMAGE)
                        .sortOrder(0)
                        .build();
                review.getMedia().add(media);
            }
            reviewMediaRepository.saveAll(review.getMedia());
        }

        // Update review summary
        updateReviewSummary(productId, review.getRating(), !review.getMedia().isEmpty());

        // Map response
        return mapToReviewResponse(review);
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponse> getReviewsByProduct(UUID productId, Short rating, boolean hasMedia, int page, int size) {
        // Verify product exists
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found");
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Review> reviews = reviewRepository.findByFilters(
                productId, ReviewStatus.APPROVED, rating, hasMedia, pageable);

        return reviews.map(this::mapToReviewResponse);
    }

    @Transactional(readOnly = true)
    public ReviewSummaryResponse getReviewSummary(UUID productId) {
        return reviewSummaryRepository.findByProductId(productId)
                .map(this::mapToSummaryResponse)
                .orElse(getEmptySummary());
    }

    private void updateReviewSummary(UUID productId, Short rating, boolean hasMedia) {
        ReviewSummary summary = reviewSummaryRepository.findByProductId(productId)
                .orElseGet(() -> ReviewSummary.builder()
                        .productId(productId)
                        .avgRating(0.0)
                        .totalCount(0L)
                        .count1Star(0L)
                        .count2Star(0L)
                        .count3Star(0L)
                        .count4Star(0L)
                        .count5Star(0L)
                        .countWithMedia(0L)
                        .build());

        // Update counts
        long newTotalCount = summary.getTotalCount() + 1;
        summary.setTotalCount(newTotalCount);

        // Update star count
        switch (rating.intValue()) {
            case 1 -> summary.setCount1Star(summary.getCount1Star() + 1);
            case 2 -> summary.setCount2Star(summary.getCount2Star() + 1);
            case 3 -> summary.setCount3Star(summary.getCount3Star() + 1);
            case 4 -> summary.setCount4Star(summary.getCount4Star() + 1);
            case 5 -> summary.setCount5Star(summary.getCount5Star() + 1);
        }

        // Update media count
        if (hasMedia) {
            summary.setCountWithMedia(summary.getCountWithMedia() + 1);
        }

        // Recalculate average rating
        double avgRating = calculateAverageRating(summary);
        summary.setAvgRating(avgRating);

        reviewSummaryRepository.save(summary);
        log.info("Updated review summary for product {}: avgRating={}, totalCount={}",
                productId, avgRating, newTotalCount);
    }

    private double calculateAverageRating(ReviewSummary summary) {
        long totalRating = summary.getCount1Star() * 1
                + summary.getCount2Star() * 2
                + summary.getCount3Star() * 3
                + summary.getCount4Star() * 4
                + summary.getCount5Star() * 5;

        if (summary.getTotalCount() == 0) {
            return 0.0;
        }
        return Math.round((double) totalRating / summary.getTotalCount() * 100.0) / 100.0;
    }

    private ReviewResponse mapToReviewResponse(Review review) {
        List<ReviewMediaResponse> mediaResponses = new ArrayList<>();
        if (review.getMedia() != null) {
            mediaResponses = review.getMedia().stream()
                    .map(m -> ReviewMediaResponse.builder()
                            .id(m.getId())
                            .reviewId(review.getId())
                            .url(m.getUrl())
                            .mediaType(m.getMediaType())
                            .sortOrder(m.getSortOrder())
                            .build())
                    .collect(Collectors.toList());
        }

        return ReviewResponse.builder()
                .id(review.getId())
                .productId(review.getProduct().getId())
                .skuId(review.getSku() != null ? review.getSku().getId() : null)
                .customerId(review.getCustomerId())
                .rating(review.getRating() != null ? review.getRating().intValue() : 0)
                .title(review.getTitle())
                .content(review.getContent())
                .media(mediaResponses)
                .createdAt(review.getCreatedAt())
                .build();
    }

    private ReviewSummaryResponse mapToSummaryResponse(ReviewSummary summary) {
        return ReviewSummaryResponse.builder()
                .avgRating(summary.getAvgRating())
                .totalCount(summary.getTotalCount() != null ? summary.getTotalCount().intValue() : 0)
                .count5star(summary.getCount5Star() != null ? summary.getCount5Star().intValue() : 0)
                .count4star(summary.getCount4Star() != null ? summary.getCount4Star().intValue() : 0)
                .count3star(summary.getCount3Star() != null ? summary.getCount3Star().intValue() : 0)
                .count2star(summary.getCount2Star() != null ? summary.getCount2Star().intValue() : 0)
                .count1star(summary.getCount1Star() != null ? summary.getCount1Star().intValue() : 0)
                .countWithMedia(summary.getCountWithMedia() != null ? summary.getCountWithMedia().intValue() : 0)
                .build();
    }

    private ReviewSummaryResponse getEmptySummary() {
        return ReviewSummaryResponse.builder()
                .avgRating(0.0)
                .totalCount(0)
                .count5star(0)
                .count4star(0)
                .count3star(0)
                .count2star(0)
                .count1star(0)
                .countWithMedia(0)
                .build();
    }
}
