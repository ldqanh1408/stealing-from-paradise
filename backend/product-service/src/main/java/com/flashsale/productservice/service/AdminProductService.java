package com.flashsale.productservice.service;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.event.payload.ProductApprovedPayload;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryRepository inventoryRepository;
    private final KafkaProducerService kafkaProducer;

    public Page<ProductResponse> getPendingProducts(String categoryId, Long sellerId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));

        if (categoryId != null && sellerId != null) {
            return productRepository.findByStatusAndCategoryIdAndDeletedAtIsNull("PENDING", categoryId, pageable)
                    .map(this::enrichResponse);
        } else if (categoryId != null) {
            return productRepository.findByStatusAndCategoryIdAndDeletedAtIsNull("PENDING", categoryId, pageable)
                    .map(this::enrichResponse);
        } else if (sellerId != null) {
            return productRepository.findByStatusAndSellerIdAndDeletedAtIsNull("PENDING", sellerId, pageable)
                    .map(this::enrichResponse);
        } else {
            return productRepository.findByStatusAndDeletedAtIsNull("PENDING", pageable)
                    .map(this::enrichResponse);
        }
    }

    public ProductResponse approveProduct(String productId, AdminApproveRequest req) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Sản phẩm không tồn tại"));

        if (!"PENDING".equals(product.getStatus())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Chỉ có thể duyệt sản phẩm ở trạng thái PENDING");
        }

        product.setStatus("APPROVED");
        product.setRejectReason(null);
        product = productRepository.save(product);

        publishProductApproved(product, req.getNote());
        return enrichResponse(product);
    }

    public ProductResponse rejectProduct(String productId, AdminRejectRequest req) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Sản phẩm không tồn tại"));

        if (!"PENDING".equals(product.getStatus())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Chỉ có thể từ chối sản phẩm ở trạng thái PENDING");
        }

        product.setStatus("REJECTED");
        product.setRejectReason(req.getReason());
        product = productRepository.save(product);

        publishProductRejected(product, req.getReason(), req.getNote());
        return enrichResponse(product);
    }

    /** Enriches a Product with category name and variants. */
    private ProductResponse enrichResponse(Product p) {
        String categoryName = null;
        if (p.getCategoryId() != null) {
            categoryName = categoryRepository.findById(p.getCategoryId())
                    .map(c -> c.getName()).orElse(null);
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
        payload.put("product_id", p.getId());
        payload.put("seller_id", p.getSellerId());
        payload.put("reason", reason);
        payload.put("note", note);
        payload.put("timestamp", System.currentTimeMillis());
        kafkaProducer.publish(KafkaTopics.PRODUCT_REJECTED, payload);
    }
}
