package com.flashsale.searchservice.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.searchservice.domain.model.SearchDocument;
import com.flashsale.searchservice.dto.event.*;
import com.flashsale.searchservice.service.ElasticsearchService;
import com.flashsale.searchservice.service.IdempotencyService;
import com.flashsale.searchservice.service.ProductServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductEventConsumer {

    private final ObjectMapper objectMapper;
    private final ElasticsearchService esService;
    private final ProductServiceClient productServiceClient;
    private final IdempotencyService idempotencyService;

    @KafkaListener(
            topics = {
                    KafkaTopics.PRODUCT_ACTIVATED,
                    KafkaTopics.PRODUCT_DEACTIVATED,
                    KafkaTopics.PRODUCT_UPDATED,
                    KafkaTopics.PRODUCT_DELETED,
                    KafkaTopics.VARIANT_PRICE_UPDATED,
                    KafkaTopics.VARIANT_STOCK_UPDATED,
                    KafkaTopics.CATEGORY_UPDATED
            },
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeProductEvent(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventId = root.has("eventId") ? root.get("eventId").asText() : null;
            String eventType = root.has("eventType") ? root.get("eventType").asText()
                    : (root.has("event") ? root.get("event").asText() : "unknown");

            if (eventId != null && idempotencyService.isProcessed(eventId)) {
                log.debug("Skipping duplicate event: {}", eventId);
                return;
            }

            log.info("Processing product event: {}", eventType);

            switch (eventType) {
                case "product.activated" -> handleProductActivated(message);
                case "product.deactivated" -> handleProductDeactivated(message);
                case "product.updated" -> handleProductUpdated(message);
                case "product.deleted" -> handleProductDeleted(message);
                case "variant.price_updated" -> handleVariantPriceUpdated(message);
                case "variant.stock_updated" -> handleVariantStockUpdated(message);
                case "category.updated" -> handleCategoryUpdated(message);
                default -> log.warn("Unknown product event type: {}", eventType);
            }

            if (eventId != null) {
                idempotencyService.markProcessed(eventId);
            }
        } catch (Exception e) {
            log.error("Failed to process product event: {}", e.getMessage(), e);
        }
    }

    private void handleProductActivated(String message) throws IOException {
        ProductActivatedPayload payload = objectMapper.readValue(message, ProductActivatedPayload.class);
        String productId = payload.getProductId();
        log.info("Indexing product {} (activated)", productId);

        List<SearchDocument> documents = productServiceClient.fetchSkuDocuments(productId);
        if (documents.isEmpty()) {
            log.warn("No SKUs found for product {}", productId);
            return;
        }

        esService.bulkIndex(documents);
        log.info("Indexed {} SKU documents for product {}", documents.size(), productId);
    }

    private void handleProductDeactivated(String message) throws IOException {
        ProductDeactivatedPayload payload = objectMapper.readValue(message, ProductDeactivatedPayload.class);
        String productId = payload.getProductId();
        log.info("Hiding product {} from search (deactivated)", productId);
        esService.setActiveByProductId(productId, false);
    }

    private void handleProductUpdated(String message) throws IOException {
        ProductUpdatedPayload payload = objectMapper.readValue(message, ProductUpdatedPayload.class);
        String productId = payload.getProductId();
        log.info("Updating product {} in index", productId);

        Map<String, Object> productData = productServiceClient.fetchProductForUpdate(productId);
        if (productData == null || productData.isEmpty()) {
            log.warn("No data found for product {} update", productId);
            return;
        }

        Map<String, Object> fields = new HashMap<>();
        if (productData.containsKey("name")) fields.put("productName", productData.get("name"));
        if (productData.containsKey("description")) fields.put("productDescription", productData.get("description"));
        if (productData.containsKey("slug")) fields.put("productSlug", productData.get("slug"));
        if (productData.containsKey("categoryId")) fields.put("categoryId", productData.get("categoryId"));
        if (productData.containsKey("categoryPath")) fields.put("categoryPath", productData.get("categoryPath"));
        if (productData.containsKey("categoryName")) fields.put("categoryName", productData.get("categoryName"));
        if (productData.containsKey("attributes")) fields.put("productAttributes", productData.get("attributes"));
        if (productData.containsKey("thumbnailUrl")) fields.put("thumbnailUrl", productData.get("thumbnailUrl"));
        if (productData.containsKey("sellerName")) fields.put("sellerName", productData.get("sellerName"));

        if (!fields.isEmpty()) {
            esService.updateByProductId(productId, fields);
            log.info("Updated {} fields for product {}", fields.size(), productId);
        }
    }

    private void handleProductDeleted(String message) throws IOException {
        ProductDeletedPayload payload = objectMapper.readValue(message, ProductDeletedPayload.class);
        String productId = payload.getProductId();
        log.info("Deleting product {} from index", productId);
        esService.deleteByProductId(productId);
    }

    private void handleVariantPriceUpdated(String message) throws IOException {
        VariantPriceUpdatedPayload payload = objectMapper.readValue(message, VariantPriceUpdatedPayload.class);
        String skuId = payload.getVariantId();
        log.info("Updating price for SKU {} (${} -> ${})", skuId, payload.getOriginalPrice(), payload.getPrice());

        Map<String, Object> fields = new HashMap<>();
        fields.put("price", payload.getPrice());
        fields.put("originalPrice", payload.getOriginalPrice());
        fields.put("hasDiscount", payload.getPrice() < payload.getOriginalPrice());

        esService.partialUpdate(skuId, fields);
    }

    private void handleVariantStockUpdated(String message) throws IOException {
        VariantStockUpdatedPayload payload = objectMapper.readValue(message, VariantStockUpdatedPayload.class);
        String skuId = payload.getVariantId();
        String stockStatus = payload.getStockStatus() != null ? payload.getStockStatus() : "out_of_stock";
        log.info("Updating stock for SKU {} to {}", skuId, stockStatus);

        Map<String, Object> fields = new HashMap<>();
        fields.put("stockStatus", stockStatus);

        esService.partialUpdate(skuId, fields);
    }

    private void handleCategoryUpdated(String message) throws IOException {
        CategoryUpdatedPayload payload = objectMapper.readValue(message, CategoryUpdatedPayload.class);
        String categoryId = payload.getCategoryId();
        log.info("Updating category {} fields in index", categoryId);

        Map<String, Object> categoryData = productServiceClient.fetchCategoryForUpdate(categoryId);
        if (categoryData == null || categoryData.isEmpty()) {
            log.warn("No data found for category {} update", categoryId);
            return;
        }

        Map<String, Object> fields = new HashMap<>();
        if (categoryData.containsKey("name")) fields.put("categoryName", categoryData.get("name"));
        if (categoryData.containsKey("path")) fields.put("categoryPath", categoryData.get("path"));

        if (!fields.isEmpty()) {
            esService.updateByCategoryId(categoryId, fields);
            log.info("Updated {} fields for category {}", fields.size(), categoryId);
        }
    }
}
