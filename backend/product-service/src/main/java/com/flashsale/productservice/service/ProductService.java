package com.flashsale.productservice.service;

import com.flashsale.commonlib.event.KafkaTopics;
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
import com.flashsale.productservice.dto.request.CreateProductRequest;
import com.flashsale.productservice.dto.request.UpdateProductRequest;
import com.flashsale.productservice.dto.response.ProductResponse;
import com.flashsale.productservice.dto.response.VariantResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.annotation.KafkaListener;
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
public class ProductService {

    public static final String DRAFT = "DRAFT";
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String ACTIVE = "ACTIVE";
    public static final String OUT_OF_STOCK = "OUT_OF_STOCK";
    public static final String INACTIVE = "INACTIVE";

    public static final String VARIANT_ACTIVE = "ACTIVE";
    public static final String VARIANT_OUT_OF_STOCK = "OUT_OF_STOCK";
    public static final String VARIANT_INACTIVE = "INACTIVE";

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryRepository inventoryRepository;
    private final KafkaProducerService kafkaProducer;

    // ─── Create ───────────────────────────────────────────────────────────────

    public ProductResponse createProduct(Long sellerId, CreateProductRequest req) {
        Category category = categoryRepository.findById(req.getCategoryId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Danh mục không tồn tại"));

        boolean hasChildren = !categoryRepository.findByParentId(category.getId()).isEmpty();
        if (hasChildren) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Phải chọn danh mục lá (không có danh mục con)");
        }

        Product product = Product.builder()
                .sellerId(sellerId)
                .categoryId(req.getCategoryId())
                .name(req.getName())
                .description(req.getDescription())
                .attributes(req.getAttributes())
                .images(req.getImages())
                .isFlashSale(false)
                .status(DRAFT)
                .rejectCount(0)
                .build();

        product = productRepository.save(product);
        publishProductCreated(product);
        return enrichResponse(product);
    }

    // ─── Update ───────────────────────────────────────────────────────────────

    public ProductResponse updateProduct(String productId, Long sellerId, UpdateProductRequest req) {
        Product product = productRepository.findByIdAndSellerId(productId, sellerId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Sản phẩm không tồn tại hoặc không thuộc seller"));

        if (req.getName() != null)        product.setName(req.getName());
        if (req.getDescription() != null) product.setDescription(req.getDescription());
        if (req.getAttributes() != null)  product.setAttributes(req.getAttributes());
        if (req.getImages() != null)      product.setImages(req.getImages());

        if (req.getCategoryId() != null) {
            Category category = categoryRepository.findById(req.getCategoryId())
                    .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Danh mục không tồn tại"));
            boolean hasChildren = !categoryRepository.findByParentId(category.getId()).isEmpty();
            if (hasChildren) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "Phải chọn danh mục lá (không có danh mục con)");
            }
            product.setCategoryId(req.getCategoryId());
        }

        product = productRepository.save(product);
        publishProductUpdated(product);
        return enrichResponse(product);
    }

    // ─── Soft Delete ──────────────────────────────────────────────────────────

    public void deleteProduct(String productId, Long sellerId) {
        Product product = productRepository.findByIdAndSellerId(productId, sellerId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Sản phẩm không tồn tại hoặc không thuộc seller"));

        String currentStatus = product.getStatus();
        boolean canDelete = DRAFT.equals(currentStatus)
                || REJECTED.equals(currentStatus)
                || INACTIVE.equals(currentStatus);

        if (!canDelete) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Chỉ có thể xóa sản phẩm ở trạng thái DRAFT, REJECTED, hoặc INACTIVE");
        }

        product.setDeletedAt(LocalDateTime.now());
        productRepository.save(product);

        publishProductDeleted(product);
    }

    // ─── Get Public Detail ────────────────────────────────────────────────────

    public ProductResponse getProduct(String productId) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Sản phẩm không tồn tại"));
        return enrichResponse(product);
    }

    // ─── Public Product Listing ───────────────────────────────────────────────

    public Page<ProductResponse> getProducts(String category, String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Product> result;

        boolean hasCategory = category != null && !category.isBlank();
        boolean hasSearch = search != null && !search.isBlank();

        if (hasCategory && hasSearch) {
            String keyword = "%" + search.toLowerCase() + "%";
            result = productRepository.findActiveByCategoryAndNameContaining(
                    category, keyword, pageable);
        } else if (hasCategory) {
            result = productRepository.findByStatusAndCategoryIdAndDeletedAtIsNull(
                    ACTIVE, category, pageable);
        } else if (hasSearch) {
            String keyword = "%" + search.toLowerCase() + "%";
            result = productRepository.findActiveByNameContaining(keyword, pageable);
        } else {
            result = productRepository.findByStatusAndDeletedAtIsNull(ACTIVE, pageable);
        }

        return result.map(this::enrichResponse);
    }

    // ─── Submit for Review ────────────────────────────────────────────────────

    public ProductResponse submitForReview(String productId, Long sellerId) {
        Product product = productRepository.findByIdAndSellerId(productId, sellerId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Sản phẩm không tồn tại hoặc không thuộc seller"));

        if (!DRAFT.equals(product.getStatus()) && !REJECTED.equals(product.getStatus())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Chỉ có thể gửi duyệt sản phẩm ở trạng thái DRAFT hoặc REJECTED");
        }

        // BR-009 Preconditions
        // 1. >=1 variant with stock>0 (from inventory)
        List<ProductVariant> variants = variantRepository.findByProductId(productId);
        if (variants.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Sản phẩm phải có ít nhất 1 biến thể");
        }

        boolean hasStock = variants.stream()
                .anyMatch(v -> {
                    Optional<Inventory> inv = inventoryRepository.findByVariantCode(v.getVariantCode());
                    return inv.map(i -> i.getStockAvailable() != null && i.getStockAvailable() > 0)
                            .orElse(false);
                });
        if (!hasStock) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Ít nhất một biến thể phải có tồn kho > 0");
        }

        // 2. >=1 image
        if (product.getImages() == null || product.getImages().isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Sản phẩm phải có ít nhất 1 hình ảnh");
        }

        // 3. name + description non-empty
        if (product.getName() == null || product.getName().isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Tên sản phẩm không được để trống");
        }
        if (product.getDescription() == null || product.getDescription().isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Mô tả sản phẩm không được để trống");
        }

        // 4. rejectCount < 3
        Integer currentRejectCount = product.getRejectCount();
        if (currentRejectCount != null && currentRejectCount >= 3) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Đã vượt quá giới hạn từ chối (tối đa 3 lần)");
        }

        product.setStatus(PENDING);
        product = productRepository.save(product);

        publishProductPendingReview(product);
        return enrichResponse(product);
    }

    // ─── Publish / Unpublish ──────────────────────────────────────────────────

    public ProductResponse publishProduct(String productId, Long sellerId) {
        Product product = productRepository.findByIdAndSellerId(productId, sellerId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Sản phẩm không tồn tại hoặc không thuộc seller"));

        String currentStatus = product.getStatus();

        // Allow: APPROVED -> ACTIVE, INACTIVE -> ACTIVE, OUT_OF_STOCK -> ACTIVE
        boolean canPublish = APPROVED.equals(currentStatus)
                || INACTIVE.equals(currentStatus)
                || OUT_OF_STOCK.equals(currentStatus);

        if (!canPublish) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Chỉ có thể mở bán sản phẩm đã được duyệt, INACTIVE, hoặc OUT_OF_STOCK");
        }

        // Auto-derive status based on variant stock
        String derivedStatus = deriveProductStatus(productId);
        product.setStatus(derivedStatus);
        product = productRepository.save(product);

        // Auto-update variant statuses
        updateVariantStatuses(productId);

        publishProductUpdated(product);
        return enrichResponse(product);
    }

    public ProductResponse unpublishProduct(String productId, Long sellerId) {
        Product product = productRepository.findByIdAndSellerId(productId, sellerId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Sản phẩm không tồn tại hoặc không thuộc seller"));

        String currentStatus = product.getStatus();

        // Allow: ACTIVE -> INACTIVE, OUT_OF_STOCK -> INACTIVE
        boolean canUnpublish = ACTIVE.equals(currentStatus) || OUT_OF_STOCK.equals(currentStatus);

        if (!canUnpublish) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Chỉ có thể tạm ẩn sản phẩm đang mở bán hoặc OUT_OF_STOCK");
        }

        product.setStatus(INACTIVE);
        product = productRepository.save(product);

        publishProductUpdated(product);
        return enrichResponse(product);
    }

    // ─── Variant Status Management ───────────────────────────────────────────

    /**
     * Update variant status based on inventory stock.
     * Auto-updates product status as well (BR-003).
     */
    public void updateVariantStatuses(String productId) {
        List<ProductVariant> variants = variantRepository.findByProductId(productId);

        for (ProductVariant variant : variants) {
            String newStatus = deriveVariantStatus(variant.getVariantCode());
            if (!newStatus.equals(variant.getStatus())) {
                variant.setStatus(newStatus);
                variantRepository.save(variant);
            }
        }

        // Auto-update product status
        Optional<Product> productOpt = productRepository.findById(productId);
        if (productOpt.isPresent()) {
            Product product = productOpt.get();
            String productNewStatus = deriveProductStatus(productId);
            if (!productNewStatus.equals(product.getStatus())) {
                product.setStatus(productNewStatus);
                productRepository.save(product);
                publishProductUpdated(product);
            }
        }
    }

    /**
     * Derive variant status from inventory stock.
     */
    private String deriveVariantStatus(String variantCode) {
        Optional<Inventory> invOpt = inventoryRepository.findByVariantCode(variantCode);
        if (invOpt.isEmpty()) {
            return VARIANT_OUT_OF_STOCK;
        }
        Inventory inv = invOpt.get();
        if (inv.getStockAvailable() == null || inv.getStockAvailable() <= 0) {
            return VARIANT_OUT_OF_STOCK;
        }
        return VARIANT_ACTIVE;
    }

    /**
     * Derive product status from all variant stock levels.
     * ACTIVE if any variant has stock, OUT_OF_STOCK otherwise.
     */
    private String deriveProductStatus(String productId) {
        List<ProductVariant> variants = variantRepository.findByProductId(productId);
        if (variants.isEmpty()) {
            return OUT_OF_STOCK;
        }

        boolean anyHasStock = variants.stream()
                .anyMatch(v -> {
                    Optional<Inventory> inv = inventoryRepository.findByVariantCode(v.getVariantCode());
                    return inv.map(i -> i.getStockAvailable() != null && i.getStockAvailable() > 0)
                            .orElse(false);
                });

        return anyHasStock ? ACTIVE : OUT_OF_STOCK;
    }

    // ─── Seller List ──────────────────────────────────────────────────────────

    public Page<ProductResponse> getSellerProducts(Long sellerId, int page, int size) {
        return productRepository.findBySellerIdAndDeletedAtIsNull(
                        sellerId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(this::enrichResponse);
    }

    // ─── Kafka Consumer ───────────────────────────────────────────────────────

    @KafkaListener(topics = KafkaTopics.PRODUCT_APPROVED, groupId = "product-service-group")
    public void onProductApproved(Object payload) {
        log.info("Received product.approved event: {}", payload);
    }

    // ─── Enrich Response ─────────────────────────────────────────────────────

    /**
     * Enriches a Product with category name, computed fields, and variant details.
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

    // ─── Kafka Producers (private helpers) ───────────────────────────────────

    private void publishProductCreated(Product p) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("product_id", p.getId());
        payload.put("seller_id", p.getSellerId());
        payload.put("name", p.getName());
        payload.put("category_id", p.getCategoryId());
        payload.put("status", p.getStatus());
        payload.put("timestamp", System.currentTimeMillis());
        kafkaProducer.publish(KafkaTopics.PRODUCT_CREATED, payload);
    }

    private void publishProductUpdated(Product p) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("product_id", p.getId());
        payload.put("seller_id", p.getSellerId());
        payload.put("status", p.getStatus());
        payload.put("timestamp", System.currentTimeMillis());
        kafkaProducer.publish(KafkaTopics.PRODUCT_UPDATED, payload);
    }

    private void publishProductDeleted(Product p) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("product_id", p.getId());
        payload.put("seller_id", p.getSellerId());
        payload.put("timestamp", System.currentTimeMillis());
        kafkaProducer.publish(KafkaTopics.PRODUCT_DELETED, payload);
    }

    private void publishProductPendingReview(Product p) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("product_id", p.getId());
        payload.put("seller_id", p.getSellerId());
        payload.put("timestamp", System.currentTimeMillis());
        kafkaProducer.publish(KafkaTopics.PRODUCT_PENDING_REVIEW, payload);
    }
}
