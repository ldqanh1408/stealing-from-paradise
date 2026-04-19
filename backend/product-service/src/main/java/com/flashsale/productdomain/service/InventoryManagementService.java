package com.flashsale.productdomain.service;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.productdomain.domain.model.Inventory;
import com.flashsale.productdomain.domain.model.ProductVariant;
import com.flashsale.productdomain.domain.repository.InventoryRepository;
import com.flashsale.productdomain.domain.repository.ProductRepository;
import com.flashsale.productdomain.domain.repository.ProductVariantRepository;
import com.flashsale.productdomain.dto.request.InventoryAdjustRequest;
import com.flashsale.productdomain.dto.request.InventoryRestockRequest;
import com.flashsale.productdomain.dto.response.InventoryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryManagementService {

    private final InventoryRepository inventoryRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final MongoTemplate mongoTemplate;
    private final KafkaProducerService kafkaProducer;

    public InventoryResponse getInventory(String skuCode) {
        Inventory inv = inventoryRepository.findBySkuCode(skuCode)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "SKU không tồn tại: " + skuCode));
        return InventoryResponse.from(inv);
    }

    /**
     * Seller restock: adds quantity to stock_total and stock_available atomically.
     */
    public InventoryResponse restock(String skuCode, Long sellerId, InventoryRestockRequest req) {
        ProductVariant variant = variantRepository.findBySkuCode(skuCode)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "SKU không tồn tại"));

        // Verify seller owns this product
        productRepository.findByIdAndSellerId(variant.getProductId(), sellerId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN, "Không có quyền chỉnh sửa tồn kho này"));

        Query query = Query.query(Criteria.where("skuCode").is(skuCode));
        Update update = new Update()
                .inc("stockTotal", req.getQuantity())
                .inc("stockAvailable", req.getQuantity());
        mongoTemplate.updateFirst(query, update, Inventory.class);

        publishInventoryAdjusted(skuCode, req.getQuantity(), req.getReason());
        return getInventory(skuCode);
    }

    /**
     * Seller manual adjust: delta can be positive (restock) or negative (correction).
     * Ensures stock_available never goes negative.
     */
    public InventoryResponse adjust(Long sellerId, InventoryAdjustRequest req) {
        ProductVariant variant = variantRepository.findBySkuCode(req.getSkuCode())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "SKU không tồn tại: " + req.getSkuCode()));

        // Verify seller owns this product
        productRepository.findByIdAndSellerId(variant.getProductId(), sellerId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN, "Không có quyền chỉnh sửa tồn kho này"));

        int delta = req.getDelta();

        if (delta < 0) {
            // Guard: stock_available must not go negative
            Query query = Query.query(
                    Criteria.where("skuCode").is(req.getSkuCode())
                            .and("stockAvailable").gte(-delta)
            );
            Update update = new Update()
                    .inc("stockTotal", delta)
                    .inc("stockAvailable", delta);
            var result = mongoTemplate.updateFirst(query, update, Inventory.class);
            if (result.getModifiedCount() == 0) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Số lượng điều chỉnh vượt quá tồn kho hiện có");
            }
        } else {
            Query query = Query.query(Criteria.where("skuCode").is(req.getSkuCode()));
            Update update = new Update()
                    .inc("stockTotal", delta)
                    .inc("stockAvailable", delta);
            mongoTemplate.updateFirst(query, update, Inventory.class);
        }

        publishInventoryAdjusted(req.getSkuCode(), delta, req.getReason());
        return getInventory(req.getSkuCode());
    }

    private void publishInventoryAdjusted(String skuCode, int delta, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sku_code", skuCode);
        payload.put("delta", delta);
        payload.put("reason", reason);
        payload.put("timestamp", System.currentTimeMillis());
        kafkaProducer.publish(KafkaTopics.INVENTORY_ADJUSTED, payload);
    }
}
