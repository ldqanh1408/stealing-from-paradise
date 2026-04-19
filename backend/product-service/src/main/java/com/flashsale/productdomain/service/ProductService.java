package com.flashsale.productdomain.service;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.productdomain.domain.model.Category;
import com.flashsale.productdomain.domain.model.Product;
import com.flashsale.productdomain.domain.repository.CategoryRepository;
import com.flashsale.productdomain.domain.repository.ProductRepository;
import com.flashsale.productdomain.dto.request.CreateProductRequest;
import com.flashsale.productdomain.dto.request.UpdateProductRequest;
import com.flashsale.productdomain.dto.response.ProductResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    // Trust-score-based PENDING product limits (Bronze ≤3, Silver ≤10, Gold ≤30, Platinum+ unlimited)
    private static final Map<String, Integer> PENDING_LIMIT_BY_TIER = Map.of(
            "BRONZE", 3,
            "SILVER", 10,
            "GOLD", 30
    );

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
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
        return ProductResponse.from(product);
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
        return ProductResponse.from(product);
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
        return ProductResponse.from(product);
    }

    // ─── Seller List ──────────────────────────────────────────────────────────

    public Page<ProductResponse> getSellerProducts(Long sellerId, int page, int size) {
        return productRepository.findBySellerIdAndDeletedAtIsNull(
                        sellerId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(ProductResponse::from);
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
        return ProductResponse.from(product);
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
        return ProductResponse.from(product);
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
        return ProductResponse.from(product);
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
