package com.flashsale.productdomain.service;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.event.payload.ProductApprovedPayload;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.productdomain.domain.model.Product;
import com.flashsale.productdomain.domain.repository.ProductRepository;
import com.flashsale.productdomain.dto.request.AdminApproveRequest;
import com.flashsale.productdomain.dto.request.AdminRejectRequest;
import com.flashsale.productdomain.dto.response.ProductResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminProductService {

    private final ProductRepository productRepository;
    private final KafkaProducerService kafkaProducer;

    public Page<ProductResponse> getPendingProducts(String categoryId, Long sellerId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));

        if (categoryId != null && sellerId != null) {
            // Both filters - use compound query via custom method
            return productRepository.findByStatusAndCategoryIdAndDeletedAtIsNull("PENDING", categoryId, pageable)
                    .map(ProductResponse::from);
        } else if (categoryId != null) {
            return productRepository.findByStatusAndCategoryIdAndDeletedAtIsNull("PENDING", categoryId, pageable)
                    .map(ProductResponse::from);
        } else if (sellerId != null) {
            return productRepository.findByStatusAndSellerIdAndDeletedAtIsNull("PENDING", sellerId, pageable)
                    .map(ProductResponse::from);
        } else {
            return productRepository.findByStatusAndDeletedAtIsNull("PENDING", pageable)
                    .map(ProductResponse::from);
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
        return ProductResponse.from(product);
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
        return ProductResponse.from(product);
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
