package com.flashsale.productservice.service;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.productservice.domain.model.Inventory;
import com.flashsale.productservice.domain.model.ProductVariant;
import com.flashsale.productservice.domain.repository.InventoryRepository;
import com.flashsale.productservice.domain.repository.ProductRepository;
import com.flashsale.productservice.domain.repository.ProductVariantRepository;
import com.flashsale.productservice.dto.request.InventoryAdjustRequest;
import com.flashsale.productservice.dto.request.InventoryRestockRequest;
import com.flashsale.productservice.dto.response.InventoryResponse;
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

    public InventoryResponse getInventory(String variantCode) {
        Inventory inv = inventoryRepository.findByVariantCode(variantCode)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "SKU không tồn tại: " + variantCode));
        return InventoryResponse.from(inv);
    }

    /**
     * Seller restock: adds quantity to stock_total and stock_available atomically.
     */
    public InventoryResponse restock(String variantCode, Long sellerId, InventoryRestockRequest req) {
        ProductVariant variant = variantRepository.findByVariantCode(variantCode)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "SKU không tồn tại"));

        // Verify seller owns this product
        productRepository.findByIdAndSellerId(variant.getProductId(), sellerId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN, "Không có quyền chỉnh sửa tồn kho này"));

        Query query = Query.query(Criteria.where("variantCode").is(variantCode));
        Update update = new Update()
                .inc("stockTotal", req.getQuantity())
                .inc("stockAvailable", req.getQuantity());
        mongoTemplate.updateFirst(query, update, Inventory.class);

        publishInventoryAdjusted(variantCode, req.getQuantity(), req.getReason());
        return getInventory(variantCode);
    }

    /**
     * Seller manual adjust: delta can be positive (restock) or negative (correction).
     * Ensures stock_available never goes negative.
     */
    public InventoryResponse adjust(Long sellerId, InventoryAdjustRequest req) {
        ProductVariant variant = variantRepository.findByVariantCode(req.getSkuCode())
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "SKU không tồn tại: " + req.getSkuCode()));

        // Verify seller owns this product
        productRepository.findByIdAndSellerId(variant.getProductId(), sellerId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN, "Không có quyền chỉnh sửa tồn kho này"));

        int delta = req.getDelta();

        if (delta < 0) {
            // Guard: stock_available must not go negative
            Query query = Query.query(
                    Criteria.where("variantCode").is(req.getSkuCode())
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
            Query query = Query.query(Criteria.where("variantCode").is(req.getSkuCode()));
            Update update = new Update()
                    .inc("stockTotal", delta)
                    .inc("stockAvailable", delta);
            mongoTemplate.updateFirst(query, update, Inventory.class);
        }

        publishInventoryAdjusted(req.getSkuCode(), delta, req.getReason());
        return getInventory(req.getSkuCode());
    }

    private void publishInventoryAdjusted(String variantCode, int delta, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("variant_code", variantCode);
        payload.put("delta", delta);
        payload.put("reason", reason);
        payload.put("timestamp", System.currentTimeMillis());
        kafkaProducer.publish(KafkaTopics.INVENTORY_ADJUSTED, payload);
    }
}
