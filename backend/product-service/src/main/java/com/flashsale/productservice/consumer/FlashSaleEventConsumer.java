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

                            emitPriceSyncEvent(variantId, flashPrice, true, variant.getProductId(),
                                    variant.getOriginalPrice() != null ? variant.getOriginalPrice() : variant.getPrice());
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

                emitPriceSyncEvent(variant.getId(), variant.getPrice(), false, variant.getProductId(), null);
            }

            log.info("Flash sale session ended processing complete: sessionId={}", sessionId);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing flash_sale.session_ended event: {}", record.value(), e);
            ack.acknowledge();
        }
    }

    private void emitPriceSyncEvent(UUID variantId, BigDecimal price, boolean active, UUID productId,
                                   BigDecimal originalPrice) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("variantId", variantId);
            payload.put("productId", productId);
            payload.put("price", price);
            payload.put("originalPrice", originalPrice);
            payload.put("active", active);
            if (active && originalPrice != null && price.compareTo(originalPrice) < 0) {
                int discountPct = originalPrice.subtract(price)
                        .multiply(java.math.BigDecimal.valueOf(100))
                        .divideToIntegralValue(originalPrice)
                        .intValue();
                payload.put("hasDiscount", true);
                payload.put("discountPct", discountPct);
            } else {
                payload.put("hasDiscount", false);
                payload.put("discountPct", 0);
            }

            String value = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(KafkaTopics.FLASH_SALE_PRICE_SYNC, variantId.toString(), value);
        } catch (Exception e) {
            log.error("Failed to emit flash_sale.price_sync event for variantId={}", variantId, e);
        }
    }

    private void emitStockUpdatedEvent(UUID variantId, int stockQuantity, UUID productId, String stockStatus) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("variantId", variantId);
            payload.put("productId", productId);
            payload.put("stockQuantity", stockQuantity);
            payload.put("status", stockStatus);
            payload.put("stockStatus", stockStatus);

            String value = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(KafkaTopics.VARIANT_STOCK_UPDATED, variantId.toString(), value);
        } catch (Exception e) {
            log.error("Failed to emit variant.stock_updated event for variantId={}", variantId, e);
        }
    }
}
