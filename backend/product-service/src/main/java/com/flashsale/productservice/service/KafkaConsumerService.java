package com.flashsale.productservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Centralized Kafka consumer service.
 * Handles events from Order Service, Flash Sale Service, and other services.
 * 
 * Events consumed:
 * - order.created: Reserve stock for checkout (delegate to StockReservationService)
 * - order.cancelled: Release reserved stock, remove cart items
 * - order.returned: Restore stock to available
 * - flash_sale.session_started: Sync flash sale prices
 * - flash_sale.session_ended: Reset flash sale prices, release unsold stock
 * - flash_sale.item_purchased: Update flash sale sold count
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerService {

    private final StockReservationService stockReservationService;
    private final CartService cartService;
    private final ObjectMapper objectMapper;

    // ─── Order Service Events ──────────────────────────────────────────────────

    /**
     * Handles order.created event.
     * Payload: { "order_id": "uuid", "session_id": "uuid", "user_id": 42, "items": [{ "sku_code": "SKU001", "quantity": 2 }] }
     * 
     * For each item, reserve stock via StockReservationService.
     */
    @KafkaListener(
            topics = KafkaTopics.ORDER_CREATED,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onOrderCreated(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            
            String orderId = payload.get("order_id") != null ? payload.get("order_id").toString() : null;
            String sessionId = payload.get("session_id") != null ? payload.get("session_id").toString() : null;
            Object itemsObj = payload.get("items");
            
            if (sessionId == null || itemsObj == null) {
                log.warn("Invalid order.created event: missing session_id or items");
                return;
            }

            var items = objectMapper.convertValue(itemsObj, new TypeReference<java.util.List<Map<String, Object>>>() {});
            for (Map<String, Object> item : items) {
                String skuCode = item.get("sku_code") != null ? item.get("sku_code").toString() : null;
                int quantity = item.get("quantity") != null ? ((Number) item.get("quantity")).intValue() : 0;
                
                if (skuCode != null && quantity > 0) {
                    try {
                        stockReservationService.reserveStock(sessionId, skuCode, quantity);
                    } catch (Exception e) {
                        log.error("Failed to reserve stock for order {} sku {}: {}", orderId, skuCode, e.getMessage());
                    }
                }
            }
            
            log.info("Processed order.created: orderId={}, sessionId={}, itemCount={}", 
                    orderId, sessionId, items.size());
                    
        } catch (Exception e) {
            log.error("Failed to process order.created event: {}", e.getMessage(), e);
        }
    }

    /**
     * Handles order.cancelled event.
     * Payload: { "order_id": "uuid", "session_id": "uuid", "user_id": 42 }
     * 
     * Release all pending reservations for this session.
     */
    @KafkaListener(
            topics = KafkaTopics.ORDER_CANCELLED,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onOrderCancelled(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            
            String orderId = payload.get("order_id") != null ? payload.get("order_id").toString() : null;
            String sessionId = payload.get("session_id") != null ? payload.get("session_id").toString() : null;
            Object userIdObj = payload.get("user_id");
            Long userId = userIdObj != null ? ((Number) userIdObj).longValue() : null;
            
            if (sessionId != null) {
                stockReservationService.releaseReservation(sessionId);
                log.info("Released reservations for cancelled order: orderId={}, sessionId={}", orderId, sessionId);
            }
            
        } catch (Exception e) {
            log.error("Failed to process order.cancelled event: {}", e.getMessage(), e);
        }
    }

    /**
     * Handles order.returned event.
     * Payload: { "order_id": "uuid", "items": [{ "sku_code": "SKU001", "quantity": 1 }] }
     * 
     * Restore stock for returned items.
     */
    @KafkaListener(
            topics = KafkaTopics.ORDER_RETURNED_RTS,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onOrderReturned(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            
            String orderId = payload.get("order_id") != null ? payload.get("order_id").toString() : null;
            Object itemsObj = payload.get("items");
            
            if (itemsObj == null) {
                log.warn("Invalid order.returned event: missing items");
                return;
            }

            var items = objectMapper.convertValue(itemsObj, new TypeReference<java.util.List<Map<String, Object>>>() {});
            for (Map<String, Object> item : items) {
                String skuCode = item.get("sku_code") != null ? item.get("sku_code").toString() : null;
                int quantity = item.get("quantity") != null ? ((Number) item.get("quantity")).intValue() : 0;
                
                if (skuCode != null && quantity > 0) {
                    stockReservationService.restoreStockOnReturn(skuCode, quantity);
                }
            }
            
            log.info("Processed order.returned: orderId={}, itemCount={}", orderId, items.size());
            
        } catch (Exception e) {
            log.error("Failed to process order.returned event: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle ORDER_CHECKOUT_COMPLETED event.
     * Remove purchased items from cart after successful checkout.
     */
    @KafkaListener(
            topics = KafkaTopics.ORDER_CHECKOUT_COMPLETED,
            groupId = "${spring.kafka.consumer.group-id}"
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
            var itemIds = objectMapper.convertValue(itemIdsObj, new TypeReference<java.util.List<String>>() {});

            cartService.removeCartItemsByIds(userId, itemIds);

            log.info("Cart items removed after checkout: userId={}, itemCount={}", userId, itemIds.size());

        } catch (Exception e) {
            log.error("Failed to handle checkout completed event: {}", e.getMessage(), e);
        }
    }

    // ─── Flash Sale Service Events ──────────────────────────────────────────────

    /**
     * Handles flash_sale.session_started event.
     * Payload: { "fs_session_id": "uuid", "items": [{ "fs_item_id": 1, "sku_code": "SKU001", "flash_price": 99.99 }] }
     * 
     * Update variant originalPrice, set flash sale price.
     */
    @KafkaListener(
            topics = KafkaTopics.FLASH_SALE_SESSION_STARTED,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onFlashSaleStarted(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            
            String sessionId = payload.get("fs_session_id") != null ? payload.get("fs_session_id").toString() : null;
            Object itemsObj = payload.get("items");
            
            if (itemsObj == null) {
                log.warn("Invalid flash_sale.session_started event: missing items");
                return;
            }

            var items = objectMapper.convertValue(itemsObj, new TypeReference<java.util.List<Map<String, Object>>>() {});
            // TODO: Update variant prices and emit flash_sale.price_sync
            // This requires VariantService dependency
            
            log.info("Processed flash_sale.session_started: sessionId={}, itemCount={}", sessionId, items.size());
            
        } catch (Exception e) {
            log.error("Failed to process flash_sale.session_started: {}", e.getMessage(), e);
        }
    }

    /**
     * Handles flash_sale.session_ended event.
     * Payload: { "fs_session_id": "uuid" }
     * 
     * Reset flash sale prices, release unsold stock.
     */
    @KafkaListener(
            topics = KafkaTopics.FLASH_SALE_SESSION_ENDED,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onFlashSaleEnded(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            
            String sessionId = payload.get("fs_session_id") != null ? payload.get("fs_session_id").toString() : null;
            // TODO: Reset prices and release unsold flash stock
            // This requires VariantService dependency
            
            log.info("Processed flash_sale.session_ended: sessionId={}", sessionId);
            
        } catch (Exception e) {
            log.error("Failed to process flash_sale.session_ended: {}", e.getMessage(), e);
        }
    }

    /**
     * Handles flash_sale.item_purchased event.
     * Payload: { "fs_item_id": 1, "sku_code": "SKU001", "quantity": 2 }
     * 
     * Update flash sale sold count.
     */
    @KafkaListener(
            topics = KafkaTopics.FLASH_SALE_ITEM_PURCHASED,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onFlashSaleItemPurchased(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            
            String skuCode = payload.get("sku_code") != null ? payload.get("sku_code").toString() : null;
            int quantity = payload.get("quantity") != null ? ((Number) payload.get("quantity")).intValue() : 0;
            
            log.debug("Flash sale item purchased: sku={}, qty={}", skuCode, quantity);
            
        } catch (Exception e) {
            log.error("Failed to process flash_sale.item_purchased: {}", e.getMessage(), e);
        }
    }
}
