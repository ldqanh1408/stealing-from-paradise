package com.flashsale.productdomain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.productdomain.domain.model.Cart;
import com.flashsale.productdomain.domain.model.CartItem;
import com.flashsale.productdomain.domain.model.Product;
import com.flashsale.productdomain.domain.model.ProductVariant;
import com.flashsale.productdomain.domain.repository.CartRepository;
import com.flashsale.productdomain.domain.repository.CartItemRepository;
import com.flashsale.productdomain.domain.repository.ProductRepository;
import com.flashsale.productdomain.domain.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * Cart Service — consolidated from standalone cart-service
 * Handles shopping cart operations and Kafka request-reply for Order Service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Respond to ORDER_CART_ITEMS_REQUEST from order-service
     * Format expected from order-service:
     * {
     *   "correlation_id": "uuid",
     *   "user_id": 42,
     *   "item_ids": ["id1", "id2"]
     * }
     */
    @KafkaListener(
            topics = KafkaTopics.ORDER_CART_ITEMS_REQUEST,
            groupId = "product-service-cart-reply"
    )
    public void onCartItemsRequest(String message) {
        try {
            Map<String, Object> request = objectMapper.readValue(message, new TypeReference<>() {});
            Object correlationIdObj = request.get("correlation_id");
            Object userIdObj = request.get("user_id");
            Object itemIdsObj = request.get("item_ids");

            if (correlationIdObj == null || userIdObj == null || itemIdsObj == null) {
                log.warn("Invalid cart items request: missing required fields");
                if (correlationIdObj != null) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("correlation_id", correlationIdObj.toString());
                    errorResponse.put("error", true);
                    kafkaTemplate.send(KafkaTopics.ORDER_CART_ITEMS_RESPONSE,
                            correlationIdObj.toString(), toJson(errorResponse));
                }
                return;
            }

            String correlationId = correlationIdObj.toString();
            Long userId = ((Number) userIdObj).longValue();
            List<String> itemIds = objectMapper.convertValue(itemIdsObj, new TypeReference<>() {});

            List<CartItem> items = cartItemRepository.findByUserId(userId).stream()
                    .filter(item -> itemIds.contains(item.getId()))
                    .toList();

            // Batch-load variants by skuCode (1 query instead of N)
            List<String> skuCodes = items.stream()
                    .map(CartItem::getSkuCode)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            Map<String, ProductVariant> variantBySku = skuCodes.isEmpty()
                    ? Map.of()
                    : productVariantRepository.findBySkuCodeIn(skuCodes).stream()
                            .collect(HashMap::new, (m, v) -> m.put(v.getSkuCode(), v), HashMap::putAll);

            // Batch-load products by productId (1 query instead of N)
            List<String> productIds = variantBySku.values().stream()
                    .map(ProductVariant::getProductId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            Map<String, Product> productById = productIds.isEmpty()
                    ? Map.of()
                    : productRepository.findAllById(productIds).stream()
                            .collect(HashMap::new, (m, p) -> m.put(p.getId(), p), HashMap::putAll);

            List<Map<String, Object>> enrichedItems = new ArrayList<>();

            for (CartItem item : items) {
                Map<String, Object> enriched = new HashMap<>();
                enriched.put("cartItemId", item.getId());
                enriched.put("skuCode", item.getSkuCode());
                enriched.put("variantId", item.getVariantId());
                enriched.put("priceSnapshot", item.getPriceSnapshot());
                enriched.put("quantity", item.getQuantity());
                enriched.put("fsItemId", item.getFsItemId());

                ProductVariant variant = variantBySku.get(item.getSkuCode());
                if (variant != null) {
                    enriched.put("variantName", variant.getTierName());
                    enriched.put("productId", variant.getProductId());

                    Product product = productById.get(variant.getProductId());
                    if (product != null) {
                        enriched.put("productName", product.getName());
                        enriched.put("sellerId", product.getSellerId());
                        if (product.getImages() != null && !product.getImages().isEmpty()) {
                            enriched.put("imageUrl", product.getImages().get(0));
                        }
                    }
                }

                enrichedItems.add(enriched);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("correlation_id", correlationId);
            response.put("items", enrichedItems);
            response.put("error", false);

            kafkaTemplate.send(KafkaTopics.ORDER_CART_ITEMS_RESPONSE, correlationId, toJson(response));
            log.debug("Cart items response sent: correlationId={}, itemCount={}", correlationId, enrichedItems.size());

        } catch (Exception e) {
            log.error("Failed to process cart items request: {}", e.getMessage(), e);
            try {
                Map<String, Object> request = objectMapper.readValue(message, new TypeReference<>() {});
                Object correlationIdObj = request.get("correlation_id");
                if (correlationIdObj != null) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("correlation_id", correlationIdObj.toString());
                    errorResponse.put("error", true);
                    kafkaTemplate.send(KafkaTopics.ORDER_CART_ITEMS_RESPONSE,
                            correlationIdObj.toString(), toJson(errorResponse));
                }
            } catch (Exception ex) {
                log.error("Failed to send error response for cart items request", ex);
            }
        }
    }

    /**
     * Handle ORDER_CHECKOUT_COMPLETED event
     * Remove purchased items from cart after successful checkout
     */
    @KafkaListener(
            topics = KafkaTopics.ORDER_CHECKOUT_COMPLETED,
            groupId = "product-service-cart-cleanup"
    )
    public void onCheckoutCompleted(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            Object userIdObj = payload.get("user_id");
            Object itemIdsObj = payload.get("item_ids");

            if (userIdObj == null || itemIdsObj == null) {
                log.warn("Invalid checkout completed event: missing fields");
                return;
            }

            Long userId = ((Number) userIdObj).longValue();
            List<String> itemIds = objectMapper.convertValue(itemIdsObj, new TypeReference<>() {});

            cartItemRepository.deleteAllById(itemIds);

            log.info("Cart items removed after checkout: userId={}, itemCount={}", userId, itemIds.size());

        } catch (Exception e) {
            log.error("Failed to handle checkout completed event: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle INVENTORY_ADJUSTED event
     * Update cart item availability when inventory changes
     */
    @KafkaListener(
            topics = KafkaTopics.INVENTORY_ADJUSTED,
            groupId = "product-service-cart-inventory"
    )
    public void onInventoryAdjusted(String message) {
        log.info("Inventory adjusted: {}", message);
        // TODO: Update cart item availability based on inventory changes
    }

    public void addItemToCart(Long userId, String skuCode, int quantity) {
        log.info("Adding item to cart - userId: {}, skuCode: {}, quantity: {}", userId, skuCode, quantity);
        // TODO: Implement
    }

    public void removeItemFromCart(Long userId, String skuCode) {
        log.info("Removing item from cart - userId: {}, skuCode: {}", userId, skuCode);
        // TODO: Implement
    }

    public void clearCart(Long userId) {
        log.info("Clearing cart for user: {}", userId);
        // TODO: Implement
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize Kafka payload: {}", e.getMessage());
            return "{}";
        }
    }
}

