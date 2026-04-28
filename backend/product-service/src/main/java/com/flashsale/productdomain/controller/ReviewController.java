package com.flashsale.productdomain.controller;

import com.flashsale.productdomain.dto.request.CreateReviewRequest;
import com.flashsale.productdomain.dto.response.ReviewResponse;
import com.flashsale.productdomain.dto.response.ReviewSummaryResponse;
import com.flashsale.productdomain.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/{productId}/reviews")
    public ResponseEntity<Page<ReviewResponse>> getReviewsByProduct(
            @PathVariable UUID productId,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false, defaultValue = "false") boolean hasMedia,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Short ratingFilter = rating != null ? rating.shortValue() : null;
        Page<ReviewResponse> reviews = reviewService.getReviewsByProduct(
                productId, ratingFilter, hasMedia, page, size);
        return ResponseEntity.ok(reviews);
    }

    @GetMapping("/{productId}/reviews/summary")
    public ResponseEntity<ReviewSummaryResponse> getReviewSummary(@PathVariable UUID productId) {
        return ResponseEntity.ok(reviewService.getReviewSummary(productId));
    }

    @PostMapping("/{productId}/reviews")
    public ResponseEntity<ReviewResponse> createReview(
            @PathVariable UUID productId,
            @RequestHeader("X-Customer-Id") Long customerId,
            @Valid @RequestBody CreateReviewRequest request) {
        ReviewResponse review = reviewService.createReview(productId, customerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(review);
    }
}
