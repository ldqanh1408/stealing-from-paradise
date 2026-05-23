package com.flashsale.productservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.dto.PageResponse;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.productservice.dto.image.ImageResponse;
import com.flashsale.productservice.dto.product.CreateProductRequest;
import com.flashsale.productservice.dto.product.PendingProductCard;
import com.flashsale.productservice.dto.product.ProductResponse;
import com.flashsale.productservice.dto.product.SellerProductCard;
import com.flashsale.productservice.dto.product.UpdateProductRequest;
import com.flashsale.productservice.dto.variant.VariantResponse;
import com.flashsale.productservice.entity.*;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.productservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository imageRepository;
    private final CategoryRepository categoryRepository;
    private final StockReservationRepository reservationRepository;
    private final CategoryService categoryService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ApiResponse<ProductResponse> getProduct(UUID productId) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Product not found"));

        return ApiResponse.success(toProductResponse(product));
    }

    @Transactional
    public ApiResponse<ProductResponse> createProduct(CreateProductRequest request, UserDetailsImpl user) {
        if (request.getCategoryId() != null) {
            categoryService.validateLeafCategory(request.getCategoryId());
        }

        String slug = generateSlug(request.getName());
        int counter = 1;
        String baseSlug = slug;
        while (productRepository.findBySlug(slug).isPresent()) {
            slug = baseSlug + "-" + counter++;
        }

        Product product = Product.builder()
                .name(request.getName())
                .slug(slug)
                .description(request.getDescription())
                .categoryId(request.getCategoryId())
                .attributes(serializeAttributes(request.getAttributes()))
                .sellerId(user.getId())
                .status(ProductStatus.ACTIVE)
                .rejectCount(0)
                .build();

        product = productRepository.save(product);

        emitEvent(KafkaTopics.PRODUCT_CREATED, product.getId().toString(), Map.of("productId", product.getId()));

        return ApiResponse.success(toProductResponse(product));
    }

    @Transactional
    public ApiResponse<ProductResponse> updateProduct(UUID productId, UpdateProductRequest request, UserDetailsImpl user) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Product not found"));

        if (!product.getSellerId().equals(user.getId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "You don't have permission to update this product");
        }

        Set<ProductStatus> updatableStatuses = Set.of(
                ProductStatus.DRAFT, ProductStatus.REJECTED, ProductStatus.APPROVED, ProductStatus.INACTIVE
        );
        if (!updatableStatuses.contains(product.getStatus())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Cannot update product in current status");
        }

        if (request.getName() != null) {
            product.setName(request.getName());
        }
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }
        if (request.getCategoryId() != null) {
            categoryService.validateLeafCategory(request.getCategoryId());
            product.setCategoryId(request.getCategoryId());
        }
        if (request.getAttributes() != null) {
            product.setAttributes(serializeAttributes(request.getAttributes()));
        }

        product = productRepository.save(product);

        emitEvent(KafkaTopics.PRODUCT_UPDATED, product.getId().toString(), Map.of("productId", product.getId()));

        return ApiResponse.success(toProductResponse(product));
    }

    @Transactional
    public ApiResponse<Void> deleteProduct(UUID productId, UserDetailsImpl user) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Product not found"));

        if (!product.getSellerId().equals(user.getId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "You don't have permission to delete this product");
        }

        List<ProductVariant> variants = variantRepository.findByProductIdAndDeletedAtIsNull(productId);
        for (ProductVariant variant : variants) {
            List<StockReservation> activeReservations = reservationRepository.findByVariantIdAndStatusAndExpiresAtBefore(
                    variant.getId(), ReservationStatus.PENDING, LocalDateTime.now().plusMinutes(1));
            if (!activeReservations.isEmpty()) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Cannot delete product with active reservations");
            }
        }

        product.setDeletedAt(LocalDateTime.now());
        productRepository.save(product);

        emitEvent(KafkaTopics.PRODUCT_DELETED, product.getId().toString(), Map.of("productId", product.getId()));

        return ApiResponse.success(null);
    }

    @Transactional(readOnly = true)
    public ApiResponse<PageResponse<SellerProductCard>> getSellerProducts(UserDetailsImpl user, Pageable pageable) {
        Page<Product> products = productRepository.findBySellerIdAndDeletedAtIsNull(user.getId(), pageable);

        List<SellerProductCard> cards = products.getContent().stream()
                .map(this::toSellerProductCard)
                .collect(Collectors.toList());

        PageResponse<SellerProductCard> pageResponse = PageResponse.<SellerProductCard>builder()
                .content(cards)
                .page(products.getNumber())
                .size(products.getSize())
                .totalElements(products.getTotalElements())
                .totalPages(products.getTotalPages())
                .last(products.isLast())
                .build();

        return ApiResponse.success(pageResponse);
    }

    @Transactional
    public ApiResponse<Void> submitForReview(UUID productId, UserDetailsImpl user) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Product not found"));

        if (!product.getSellerId().equals(user.getId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "You don't have permission to submit this product");
        }

        if (!canTransition(product.getStatus(), ProductStatus.PENDING)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Cannot submit product for review from current status");
        }

        if (product.getRejectCount() >= 3) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Product has been rejected 3 times. Please contact admin.");
        }

        List<ProductVariant> variants = variantRepository.findByProductIdAndDeletedAtIsNull(productId);
        if (variants.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Product must have at least one variant");
        }

        List<ProductImage> images = imageRepository.findByProductIdOrderBySortOrderAsc(productId);
        if (images.isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Product must have at least one image");
        }

        product.setStatus(ProductStatus.PENDING);
        product.setSubmittedAt(LocalDateTime.now());
        productRepository.save(product);

        emitEvent(KafkaTopics.PRODUCT_PENDING_REVIEW, product.getId().toString(), Map.of("productId", product.getId()));

        return ApiResponse.success(null);
    }

    @Transactional
    public ApiResponse<Void> publishProduct(UUID productId, UserDetailsImpl user) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Product not found"));

        if (!product.getSellerId().equals(user.getId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "You don't have permission to publish this product");
        }

        if (!canTransition(product.getStatus(), ProductStatus.ACTIVE)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Cannot publish product from current status");
        }

        if (product.getPublishedAt() == null) {
            product.setPublishedAt(LocalDateTime.now());
        }

        product.setStatus(ProductStatus.ACTIVE);
        productRepository.save(product);

        emitEvent(KafkaTopics.PRODUCT_UPDATED, product.getId().toString(), Map.of("productId", product.getId()));

        return ApiResponse.success(null);
    }

    @Transactional
    public ApiResponse<Void> unpublishProduct(UUID productId, UserDetailsImpl user) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Product not found"));

        if (!product.getSellerId().equals(user.getId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "You don't have permission to unpublish this product");
        }

        if (!canTransition(product.getStatus(), ProductStatus.INACTIVE)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Cannot unpublish product from current status");
        }

        product.setStatus(ProductStatus.INACTIVE);
        productRepository.save(product);

        emitEvent(KafkaTopics.PRODUCT_UPDATED, product.getId().toString(), Map.of("productId", product.getId()));

        return ApiResponse.success(null);
    }

    @Transactional(readOnly = true)
    public ApiResponse<PageResponse<PendingProductCard>> getPendingProducts(Pageable pageable, UUID categoryId, Long sellerId) {
        Page<Product> products = productRepository.findPendingProducts(categoryId, sellerId, pageable);

        List<PendingProductCard> cards = products.getContent().stream()
                .map(this::toPendingProductCard)
                .collect(Collectors.toList());

        PageResponse<PendingProductCard> pageResponse = PageResponse.<PendingProductCard>builder()
                .content(cards)
                .page(products.getNumber())
                .size(products.getSize())
                .totalElements(products.getTotalElements())
                .totalPages(products.getTotalPages())
                .last(products.isLast())
                .build();

        return ApiResponse.success(pageResponse);
    }

    @Transactional
    public ApiResponse<Void> approveProduct(UUID productId, UserDetailsImpl user) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Product not found"));

        if (product.getStatus() != ProductStatus.PENDING) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Product is not pending review");
        }

        product.setStatus(ProductStatus.APPROVED);
        product.setReviewedAt(LocalDateTime.now());
        product.setReviewedBy(user.getId());
        productRepository.save(product);

        emitEvent(KafkaTopics.PRODUCT_APPROVED, product.getId().toString(),
                Map.of(
                        "productId", product.getId(),
                        "sellerId", product.getSellerId(),
                        "reviewedBy", user.getId(),
                        "reviewedAt", LocalDateTime.now().toString(),
                        "rejectCount", product.getRejectCount(),
                        "note", "San pham dat yeu cau"
                ));

        return ApiResponse.success(null);
    }

    @Transactional
    public ApiResponse<Void> rejectProduct(UUID productId, String reason, UserDetailsImpl user) {
        if (reason == null || reason.length() < 10) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Rejection reason must be at least 10 characters");
        }

        Product product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Product not found"));

        if (product.getStatus() != ProductStatus.PENDING) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Product is not pending review");
        }

        product.setStatus(ProductStatus.REJECTED);
        product.setRejectReason(reason);
        product.setRejectCount(product.getRejectCount() + 1);
        product.setReviewedAt(LocalDateTime.now());
        product.setReviewedBy(user.getId());
        productRepository.save(product);

        emitEvent(KafkaTopics.PRODUCT_REJECTED, product.getId().toString(),
                Map.of(
                        "productId", product.getId(),
                        "sellerId", product.getSellerId(),
                        "reviewedBy", user.getId(),
                        "reviewedAt", LocalDateTime.now().toString(),
                        "rejectReason", reason,
                        "rejectCount", product.getRejectCount()
                ));

        return ApiResponse.success(null);
    }

    public boolean canTransition(ProductStatus from, ProductStatus to) {
        if (from == to) {
            return true;
        }

        return switch (from) {
            case DRAFT -> to == ProductStatus.PENDING;
            case PENDING -> to == ProductStatus.APPROVED || to == ProductStatus.REJECTED;
            case REJECTED -> to == ProductStatus.DRAFT || to == ProductStatus.PENDING;
            case APPROVED -> to == ProductStatus.ACTIVE || to == ProductStatus.INACTIVE;
            case ACTIVE -> to == ProductStatus.OUT_OF_STOCK || to == ProductStatus.INACTIVE;
            case OUT_OF_STOCK -> to == ProductStatus.ACTIVE || to == ProductStatus.INACTIVE;
            case INACTIVE -> to == ProductStatus.ACTIVE || to == ProductStatus.APPROVED;
        };
    }

    private ProductResponse toProductResponse(Product product) {
        List<ProductVariant> variants = variantRepository.findByProductIdAndDeletedAtIsNull(product.getId());
        List<ProductImage> images = imageRepository.findByProductIdOrderBySortOrderAsc(product.getId());
        String categoryName = null;
        if (product.getCategoryId() != null) {
            categoryName = categoryRepository.findById(product.getCategoryId())
                    .map(Category::getName)
                    .orElse(null);
        }

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .categoryId(product.getCategoryId())
                .categoryName(categoryName)
                .sellerId(product.getSellerId())
                .status(product.getStatus().name())
                .attributes(deserializeAttributes(product.getAttributes()))
                .variants(variants.stream().map(this::toVariantResponse).collect(Collectors.toList()))
                .images(images.stream().map(this::toImageResponse).collect(Collectors.toList()))
                .rejectReason(product.getRejectReason())
                .rejectCount(product.getRejectCount())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .publishedAt(product.getPublishedAt())
                .build();
    }

    private VariantResponse toVariantResponse(ProductVariant variant) {
        return VariantResponse.builder()
                .id(variant.getId())
                .productId(variant.getProductId())
                .variantCode(variant.getVariantCode())
                .variantName(variant.getVariantName())
                .variantAttributes(deserializeAttributes(variant.getVariantAttributes()))
                .price(variant.getPrice())
                .originalPrice(variant.getOriginalPrice())
                .stockQuantity(variant.getStockQuantity())
                .status(variant.getStatus().name())
                .imageUrl(variant.getImageUrl())
                .createdAt(variant.getCreatedAt())
                .updatedAt(variant.getUpdatedAt())
                .build();
    }

    private ImageResponse toImageResponse(ProductImage image) {
        return ImageResponse.builder()
                .id(image.getId())
                .productId(image.getProductId())
                .variantId(image.getVariantId())
                .url(image.getUrl())
                .sortOrder(image.getSortOrder())
                .build();
    }

    private SellerProductCard toSellerProductCard(Product product) {
        Integer variantCount = variantRepository.countByProductId(product.getId());
        Integer totalStock = variantRepository.getTotalStockByProductId(product.getId());
        String thumbnailUrl = imageRepository.findByProductIdOrderBySortOrderAsc(product.getId())
                .stream()
                .findFirst()
                .map(ProductImage::getUrl)
                .orElse(null);

        return SellerProductCard.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .status(product.getStatus().name())
                .thumbnailUrl(thumbnailUrl)
                .variantCount(variantCount != null ? variantCount : 0)
                .totalStock(totalStock != null ? totalStock : 0)
                .createdAt(product.getCreatedAt())
                .build();
    }

    private PendingProductCard toPendingProductCard(Product product) {
        String categoryName = null;
        if (product.getCategoryId() != null) {
            categoryName = categoryRepository.findById(product.getCategoryId())
                    .map(Category::getName)
                    .orElse(null);
        }

        return PendingProductCard.builder()
                .id(product.getId())
                .name(product.getName())
                .sellerId(product.getSellerId())
                .categoryId(product.getCategoryId())
                .categoryName(categoryName)
                .submittedAt(product.getSubmittedAt())
                .rejectCount(product.getRejectCount())
                .rejectReason(product.getRejectReason())
                .build();
    }

    private String serializeAttributes(Map<String, Object> attributes) {
        if (attributes == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attributes);
        } catch (JsonProcessingException e) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Invalid attributes format");
        }
    }

    private Map<String, Object> deserializeAttributes(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String generateSlug(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    private void emitEvent(String topic, String key, Map<String, Object> payload) {
        try {
            String value = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(topic, key, value);
        } catch (Exception e) {
            log.error("Failed to emit Kafka event: topic={}, key={}", topic, key, e);
        }
    }
}
