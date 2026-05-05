package com.flashsale.productservice.service;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.productservice.domain.model.Category;
import com.flashsale.productservice.domain.model.Product;
import com.flashsale.productservice.domain.model.ProductVariant;
import com.flashsale.productservice.domain.repository.CategoryRepository;
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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductVariantRepository variantRepository;
    private final KafkaProducerService kafkaProducer;

    // ─── Create ───────────────────────────────────────────────────────────────

    public ProductResponse createProduct(Long sellerId, CreateProductRequest req) {
        Category category = categoryRepository.findById(req.getCategoryId())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Danh mục không tồn tại"));

        // Leaf category check: no children allowed
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
                .isFlash(false)
                .status("DRAFT")
                .stockAvailable(0)
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

        if (product.getStockAvailable() != null && product.getStockAvailable() > 0) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "Không thể xóa sản phẩm còn hàng đang bị giữ bởi đơn hàng");
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
            result = productRepository.findPublishedByCategoryAndNameContaining(
                    category, keyword, pageable);
        } else if (hasCategory) {
            result = productRepository.findByStatusAndCategoryIdAndDeletedAtIsNull(
                    "PUBLISHED", category, pageable);
        } else if (hasSearch) {
            String keyword = "%" + search.toLowerCase() + "%";
            result = productRepository.findPublishedByNameContaining(keyword, pageable);
        } else {
            result = productRepository.findByStatusAndDeletedAtIsNull("PUBLISHED", pageable);
        }

        return result.map(this::enrichResponse);
    }

    /** Enriches a Product with category name and variants. */
    private ProductResponse enrichResponse(Product p) {
        String categoryName = null;
        String categorySlug = null;
        if (p.getCategoryId() != null) {
            Optional<Category> cat = categoryRepository.findById(p.getCategoryId());
            categoryName = cat.map(Category::getName).orElse(null);
            categorySlug = cat.map(Category::getSlug).orElse(null);
        }

        List<ProductVariant> variants = variantRepository.findByProductId(p.getId());

        Long price = null;
        Long originalPrice = null;
        if (!variants.isEmpty()) {
            price = variants.get(0).getPrice().longValue();
            originalPrice = price;
        }

        List<VariantResponse> variantResponses = variants.stream()
                .map(v -> VariantResponse.builder()
                        .variantId(v.getId())
                        .productId(v.getProductId())
                        .skuCode(v.getSkuCode())
                        .tierName(v.getTierName())
                        .price(v.getPrice())
                        .createdAt(v.getCreatedAt())
                        .updatedAt(v.getUpdatedAt())
                        .build())
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
                .isFlash(p.getIsFlash())
                .status(p.getStatus())
                .rejectReason(p.getRejectReason())
                .stockAvailable(p.getStockAvailable())
                .price(price)
                .originalPrice(originalPrice)
                .variants(variantResponses)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    // ─── Seller List ──────────────────────────────────────────────────────────

    public Page<ProductResponse> getSellerProducts(Long sellerId, int page, int size) {
        return productRepository.findBySellerIdAndDeletedAtIsNull(
                        sellerId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(this::enrichResponse);
    }

    // ─── Submit for Review ────────────────────────────────────────────────────

    public ProductResponse submitForReview(String productId, Long sellerId) {
        Product product = productRepository.findByIdAndSellerId(productId, sellerId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Sản phẩm không tồn tại hoặc không thuộc seller"));

        if (!"DRAFT".equals(product.getStatus()) && !"REJECTED".equals(product.getStatus())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Chỉ có thể gửi duyệt sản phẩm ở trạng thái DRAFT hoặc REJECTED");
        }

        product.setStatus("PENDING");
        product = productRepository.save(product);

        publishProductPendingReview(product);
        return enrichResponse(product);
    }

    // ─── Publish / Unpublish ──────────────────────────────────────────────────

    public ProductResponse publishProduct(String productId, Long sellerId) {
        Product product = productRepository.findByIdAndSellerId(productId, sellerId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Sản phẩm không tồn tại hoặc không thuộc seller"));

        if (!"APPROVED".equals(product.getStatus())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Chỉ có thể mở bán sản phẩm đã được duyệt");
        }

        product.setStatus("PUBLISHED");
        product = productRepository.save(product);
        publishProductUpdated(product);
        return enrichResponse(product);
    }

    public ProductResponse unpublishProduct(String productId, Long sellerId) {
        Product product = productRepository.findByIdAndSellerId(productId, sellerId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Sản phẩm không tồn tại hoặc không thuộc seller"));

        if (!"PUBLISHED".equals(product.getStatus())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Chỉ có thể tạm ẩn sản phẩm đang mở bán");
        }

        product.setStatus("UNPUBLISHED");
        product = productRepository.save(product);
        publishProductUpdated(product);
        return enrichResponse(product);
    }

    // ─── Kafka Consumer ───────────────────────────────────────────────────────

    @KafkaListener(topics = KafkaTopics.PRODUCT_APPROVED, groupId = "product-service-group")
    public void onProductApproved(Object payload) {
        log.info("Received product.approved event: {}", payload);
        // Status is set by AdminProductService.approveProduct(); this listener can
        // be used for downstream side-effects (e.g., search index sync).
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
