package com.flashsale.productservice.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.productservice.entity.ProductVariant;
import com.flashsale.productservice.entity.VariantStatus;
import com.flashsale.productservice.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class FlashSaleEventConsumer {

    private final ProductVariantRepository variantRepository;
    private final ObjectMapper objectMapper;
    private final org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;

    @KafkaListener(topics = "${kafka.topics.flash-sale-session-started:flash_sale.session_started}", groupId = "product-service-group")
    public void onSessionStarted(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            String sessionId = payload.has("sessionId") ? payload.get("sessionId").asText() : null;

            log.info("Received flash_sale.session_started event: sessionId={}", sessionId);

            if (payload.has("flashPriceMap") && payload.get("flashPriceMap").isObject()) {
                JsonNode flashPriceMap = payload.get("flashPriceMap");
                flashPriceMap.fieldNames().forEachRemaining(variantIdStr -> {
                    try {
                        UUID variantId = UUID.fromString(variantIdStr);
                        BigDecimal flashPrice = new BigDecimal(flashPriceMap.get(variantIdStr).asText());

                        variantRepository.findById(variantId).ifPresent(variant -> {
                            if (variant.getOriginalPrice() == null) {
                                variant.setOriginalPrice(variant.getPrice());
                            }
                            variant.setPrice(flashPrice);
                            variantRepository.save(variant);

                            log.info("Applied flash price to variant: variantId={}, originalPrice={}, flashPrice={}",
                                    variantId, variant.getOriginalPrice(), flashPrice);

                            emitPriceSyncEvent(variantId, flashPrice, true);
                        });
                    } catch (Exception e) {
                        log.error("Failed to apply flash price for variantId={}: {}", variantIdStr, e.getMessage());
                    }
                });
            }

            log.info("Flash sale session started processing complete: sessionId={}", sessionId);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing flash_sale.session_started event: {}", record.value(), e);
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = "${kafka.topics.flash-sale-session-ended:flash_sale.session_ended}", groupId = "product-service-group")
    public void onSessionEnded(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            String sessionId = payload.has("sessionId") ? payload.get("sessionId").asText() : null;

            log.info("Received flash_sale.session_ended event: sessionId={}", sessionId);

            List<ProductVariant> variantsWithOriginalPrice = variantRepository.findByOriginalPriceNotNull();
            for (ProductVariant variant : variantsWithOriginalPrice) {
                variant.setPrice(variant.getOriginalPrice());
                variant.setOriginalPrice(null);
                variantRepository.save(variant);

                log.info("Restored original price for variant: variantId={}, restoredPrice={}",
                        variant.getId(), variant.getPrice());

                emitPriceSyncEvent(variant.getId(), variant.getPrice(), false);
            }

            log.info("Flash sale session ended processing complete: sessionId={}", sessionId);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing flash_sale.session_ended event: {}", record.value(), e);
            ack.acknowledge();
        }
    }

    @KafkaListener(topics = "${kafka.topics.flash-sale-item-purchased:flash_sale.item_purchased}", groupId = "product-service-group")
    public void onItemPurchased(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            String variantIdStr = payload.has("variantId") ? payload.get("variantId").asText() : null;
            int quantity = payload.has("quantity") ? payload.get("quantity").asInt() : 0;
            Integer remainingFlashStock = payload.has("remainingFlashStock") && !payload.get("remainingFlashStock").isNull()
                    ? payload.get("remainingFlashStock").asInt() : null;

            log.info("Received flash_sale.item_purchased event: variantId={}, quantity={}, remainingFlashStock={}",
                    variantIdStr, quantity, remainingFlashStock);

            if (variantIdStr != null) {
                UUID variantId = UUID.fromString(variantIdStr);
                variantRepository.findById(variantId).ifPresent(variant -> {
                    int newStock;
                    if (remainingFlashStock != null) {
                        newStock = remainingFlashStock;
                    } else {
                        newStock = variant.getStockQuantity() - quantity;
                    }

                    variant.setStockQuantity(Math.max(0, newStock));

                    if (variant.getStockQuantity() == 0) {
                        variant.setStatus(VariantStatus.OUT_OF_STOCK);
                    }

                    variantRepository.save(variant);

                    log.info("Updated variant stock after flash sale purchase: variantId={}, newStock={}, status={}",
                            variantId, variant.getStockQuantity(), variant.getStatus());

                    emitStockUpdatedEvent(variantId, variant.getStockQuantity());
                });
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing flash_sale.item_purchased event: {}", record.value(), e);
            ack.acknowledge();
        }
    }

    private void emitPriceSyncEvent(UUID variantId, BigDecimal price, boolean active) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("variantId", variantId);
            payload.put("price", price);
            payload.put("active", active);

            String value = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(KafkaTopics.FLASH_SALE_PRICE_SYNC, variantId.toString(), value);
        } catch (Exception e) {
            log.error("Failed to emit flash_sale.price_sync event for variantId={}", variantId, e);
        }
    }

    private void emitStockUpdatedEvent(UUID variantId, int stockQuantity) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("variantId", variantId);
            payload.put("stockQuantity", stockQuantity);

            String value = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(KafkaTopics.VARIANT_STOCK_UPDATED, variantId.toString(), value);
        } catch (Exception e) {
            log.error("Failed to emit variant.stock_updated event for variantId={}", variantId, e);
        }
    }
}
