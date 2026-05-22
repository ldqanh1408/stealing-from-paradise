package com.flashsale.productservice.service;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.event.payload.ProductApprovedPayload;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.productservice.domain.model.Category;
import com.flashsale.productservice.domain.model.Inventory;
import com.flashsale.productservice.domain.model.Product;
import com.flashsale.productservice.domain.model.ProductVariant;
import com.flashsale.productservice.domain.repository.CategoryRepository;
import com.flashsale.productservice.domain.repository.InventoryRepository;
import com.flashsale.productservice.domain.repository.ProductRepository;
import com.flashsale.productservice.domain.repository.ProductVariantRepository;
import com.flashsale.productservice.dto.request.AdminApproveRequest;
import com.flashsale.productservice.dto.request.AdminRejectRequest;
import com.flashsale.productservice.dto.response.ProductResponse;
import com.flashsale.productservice.dto.response.VariantResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminProductService {

    public static final String DRAFT = "DRAFT";
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryRepository inventoryRepository;
    private final KafkaProducerService kafkaProducer;

    /**
     * Get pending products for admin review.
     * Sorted by createdAt ASC (FIFO per UC-013).
     */
    public Page<ProductResponse> getPendingProducts(String categoryId, Long sellerId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));

        if (categoryId != null && sellerId != null) {
            return productRepository.findByStatusAndCategoryIdAndDeletedAtIsNull(PENDING, categoryId, pageable)
                    .map(this::enrichResponse);
        } else if (categoryId != null) {
            return productRepository.findByStatusAndCategoryIdAndDeletedAtIsNull(PENDING, categoryId, pageable)
                    .map(this::enrichResponse);
        } else if (sellerId != null) {
            return productRepository.findByStatusAndSellerIdAndDeletedAtIsNull(PENDING, sellerId, pageable)
                    .map(this::enrichResponse);
        } else {
            return productRepository.findByStatusAndDeletedAtIsNull(PENDING, pageable)
                    .map(this::enrichResponse);
        }
    }

    /**
     * Approve a product for sale.
     * Sets reviewedAt, reviewedBy, clears rejectReason, resets rejectCount.
     */
    public ProductResponse approveProduct(String productId, AdminApproveRequest req, Long adminId) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Sản phẩm không tồn tại"));

        if (!PENDING.equals(product.getStatus())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Chỉ có thể duyệt sản phẩm ở trạng thái PENDING");
        }

        product.setStatus(APPROVED);
        product.setReviewedAt(LocalDateTime.now());
        product.setReviewedBy(adminId);
        product.setRejectReason(null);
        product.setRejectCount(0);

        product = productRepository.save(product);

        publishProductApproved(product, req.getNote());
        return enrichResponse(product);
    }

    /**
     * Reject a product submission.
     * Sets reviewedAt, reviewedBy, sets rejectReason, increments rejectCount.
     * Throws if rejectCount >= 3 (per BR-009.8).
     */
    public ProductResponse rejectProduct(String productId, AdminRejectRequest req, Long adminId) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Sản phẩm không tồn tại"));

        if (!PENDING.equals(product.getStatus())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Chỉ có thể từ chối sản phẩm ở trạng thái PENDING");
        }

        // Validate reason >= 10 chars
        String reason = req.getReason();
        if (reason == null || reason.length() < 10) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Lý do từ chối phải có ít nhất 10 ký tự");
        }

        Integer currentRejectCount = product.getRejectCount();
        if (currentRejectCount == null) {
            currentRejectCount = 0;
        }

        // BR-009.8: If rejectCount >= 3, reject
        if (currentRejectCount >= 3) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Đã vượt quá giới hạn từ chối");
        }

        product.setStatus(REJECTED);
        product.setReviewedAt(LocalDateTime.now());
        product.setReviewedBy(adminId);
        product.setRejectReason(reason);
        product.setRejectCount(currentRejectCount + 1);

        product = productRepository.save(product);

        publishProductRejected(product, reason, req.getNote());
        return enrichResponse(product);
    }

    // ─── Enrich Response ─────────────────────────────────────────────────────

    /**
     * Enriches a Product with category name, computed fields, submitter info,
     * review info, and variant details.
     */
    private ProductResponse enrichResponse(Product p) {
        String categoryName = null;
        String categorySlug = null;
        if (p.getCategoryId() != null) {
            Optional<Category> cat = categoryRepository.findById(p.getCategoryId());
            categoryName = cat.map(Category::getName).orElse(null);
            categorySlug = cat.map(Category::getSlug).orElse(null);
        }

        List<ProductVariant> variants = variantRepository.findByProductId(p.getId());

        // Compute totalStock from inventory
        int totalStock = 0;
        BigDecimal minPrice = null;
        BigDecimal maxPrice = null;
        boolean hasDiscount = false;

        for (ProductVariant variant : variants) {
            Optional<Inventory> inv = inventoryRepository.findByVariantCode(variant.getVariantCode());
            if (inv.isPresent() && inv.get().getStockAvailable() != null) {
                totalStock += inv.get().getStockAvailable();
            }

            if (variant.getPrice() != null) {
                if (minPrice == null || variant.getPrice().compareTo(minPrice) < 0) {
                    minPrice = variant.getPrice();
                }
                if (maxPrice == null || variant.getPrice().compareTo(maxPrice) > 0) {
                    maxPrice = variant.getPrice();
                }
            }

            if (variant.getOriginalPrice() != null && variant.getPrice() != null
                    && variant.getOriginalPrice().compareTo(variant.getPrice()) > 0) {
                hasDiscount = true;
            }
        }

        List<VariantResponse> variantResponses = variants.stream()
                .map(v -> {
                    Integer stock = null;
                    Optional<Inventory> inv = inventoryRepository.findByVariantCode(v.getVariantCode());
                    if (inv.isPresent()) {
                        stock = inv.get().getStockAvailable();
                    }
                    return VariantResponse.builder()
                            .variantId(v.getId())
                            .productId(v.getProductId())
                            .variantCode(v.getVariantCode())
                            .variantName(v.getVariantName())
                            .variantAttributes(v.getVariantAttributes())
                            .price(v.getPrice())
                            .originalPrice(v.getOriginalPrice())
                            .stockQuantity(v.getStockQuantity())
                            .status(v.getStatus())
                            .imageUrl(v.getImageUrl())
                            .createdAt(v.getCreatedAt())
                            .updatedAt(v.getUpdatedAt())
                            .stock(stock)
                            .build();
                })
                .toList();

        return ProductResponse.builder()
                .productId(p.getId())
                .sellerId(p.getSellerId())
                .name(p.getName())
                .description(p.getDescription())
                .categoryId(p.getCategoryId())
                .categoryName(categoryName)
                .categorySlug(categorySlug)
                .attributes(p.getAttributes())
                .images(p.getImages())
                .isFlash(p.getIsFlashSale())
                .status(p.getStatus())
                .rejectReason(p.getRejectReason())
                .reviewedAt(p.getReviewedAt())
                .reviewedBy(p.getReviewedBy())
                .rejectCount(p.getRejectCount())
                .slug(p.getSlug())
                .variants(variantResponses)
                .totalStock(totalStock)
                .minPrice(minPrice != null ? minPrice.longValue() : null)
                .maxPrice(maxPrice != null ? maxPrice.longValue() : null)
                .hasDiscount(hasDiscount)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    // ─── Kafka events ─────────────────────────────────────────────────────────

    private void publishProductApproved(Product p, String note) {
        ProductApprovedPayload payload = ProductApprovedPayload.builder()
                .eventType(KafkaTopics.PRODUCT_APPROVED)
                .productId(p.getId())
                .sellerId(String.valueOf(p.getSellerId()))
                .productName(p.getName())
                .categoryId(p.getCategoryId())
                .build();
        kafkaProducer.publish(KafkaTopics.PRODUCT_APPROVED, payload);
    }

    private void publishProductRejected(Product p, String reason, String note) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", KafkaTopics.PRODUCT_REJECTED);
        payload.put("productId", p.getId());
        payload.put("sellerId", String.valueOf(p.getSellerId()));
        payload.put("productName", p.getName());
        payload.put("reason", reason);
        payload.put("reviewedBy", p.getReviewedBy());
        payload.put("reviewedAt", p.getReviewedAt());
        payload.put("rejectCount", p.getRejectCount());
        payload.put("note", note);
        payload.put("timestamp", System.currentTimeMillis());
        kafkaProducer.publish(KafkaTopics.PRODUCT_REJECTED, payload);
    }
}
