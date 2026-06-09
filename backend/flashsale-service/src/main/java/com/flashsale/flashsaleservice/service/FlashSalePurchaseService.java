package com.flashsale.flashsaleservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.flashsaleservice.domain.model.FlashSaleItem;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleItemRepository;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleSessionRepository;
import com.flashsale.flashsaleservice.dto.request.BuyRequest;
import com.flashsale.flashsaleservice.dto.response.BuyResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class FlashSalePurchaseService {

    private final FlashSaleSessionRepository sessionRepo;
    private final FlashSaleItemRepository itemRepo;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    private final Map<String, CompletableFuture<Map<String, Object>>> pendingAddressRequests = new ConcurrentHashMap<>();

    private static final String LUA_DECR =
            "local key = KEYS[1]\n" +
            "local quantity = tonumber(ARGV[1])\n" +
            "local stock = redis.call('get', key)\n" +
            "if not stock then\n" +
            "    return -1\n" +
            "end\n" +
            "stock = tonumber(stock)\n" +
            "if stock < quantity then\n" +
            "    return -2\n" +
            "end\n" +
            "redis.call('decrby', key, quantity)\n" +
            "return stock - quantity";

    public FlashSalePurchaseService(
            FlashSaleSessionRepository sessionRepo,
            FlashSaleItemRepository itemRepo,
            ReactiveStringRedisTemplate redisTemplate,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${product-service.url:http://localhost:8084}") String productServiceUrl) {
        this.sessionRepo = sessionRepo;
        this.itemRepo = itemRepo;
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder().baseUrl(productServiceUrl).build();
    }

    public void onAddressResponse(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            Object correlationIdObj = payload.get("correlation_id");
            if (correlationIdObj != null) {
                String correlationId = correlationIdObj.toString();
                CompletableFuture<Map<String, Object>> future = pendingAddressRequests.get(correlationId);
                if (future != null) {
                    future.complete(payload);
                }
            }
        } catch (Exception e) {
            log.error("Failed to process address response: {}", e.getMessage());
        }
    }

    public Mono<BuyResponse> buyFlashSaleItem(Long sessionId, Long userId, BuyRequest req) {
        return itemRepo.findById(req.getFsItemId())
                .switchIfEmpty(Mono.error(new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy sản phẩm Flash Sale")))
                .flatMap(item -> {
                    if (!"APPROVED".equals(item.getStatus())) {
                        return Mono.error(new AppException(ErrorCode.BAD_REQUEST, "Sản phẩm chưa được duyệt"));
                    }
                    if (req.getQuantity() > item.getLimitPerUser()) {
                        return Mono.error(new AppException(ErrorCode.LIMIT_PER_USER_EXCEEDED, "Vượt quá giới hạn mua cho mỗi người dùng"));
                    }

                    return sessionRepo.findById(item.getSessionId())
                            .switchIfEmpty(Mono.error(new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy phiên Flash Sale")))
                            .flatMap(session -> {
                                if (session.getDeletedAt() != null) {
                                    return Mono.error(new AppException(ErrorCode.NOT_FOUND, "Phiên Flash Sale đã bị xóa"));
                                }
                                if (!"ACTIVE".equals(session.getStatus())) {
                                    return Mono.error(new AppException(ErrorCode.BAD_REQUEST, "Phiên Flash Sale không hoạt động"));
                                }
                                if (session.getEndTime().isBefore(LocalDateTime.now())) {
                                    return Mono.error(new AppException(ErrorCode.FLASH_SALE_ENDED, "Phiên Flash Sale đã kết thúc"));
                                }

                                return Mono.zip(
                                        fetchAddress(userId, req.getAddressId()),
                                        fetchVariantDetails(item.getSkuCode())
                                ).flatMap(tuple -> {
                                    Map<String, Object> addressInfo = tuple.getT1();
                                    Map<String, Object> variantInfo = tuple.getT2();

                                    BigDecimal totalAmount = item.getFlashPrice().multiply(BigDecimal.valueOf(req.getQuantity()));

                                    return getOrInitAndDecrementStock(item, req.getQuantity())
                                            .flatMap(newStock -> {
                                                if (newStock == -2) {
                                                    return Mono.error(new AppException(ErrorCode.INSUFFICIENT_STOCK, "Sản phẩm đã hết hàng hoặc không đủ số lượng"));
                                                }

                                                item.setSoldQty(item.getSoldQty() + req.getQuantity());
                                                return itemRepo.save(item)
                                                        .doOnSuccess(saved -> {
                                                            try {
                                                                publishCheckoutSubmittedEvent(userId, item, req.getQuantity(), totalAmount, addressInfo, variantInfo);
                                                            } catch (Exception e) {
                                                                log.error("Failed to publish order.checkout_submitted event", e);
                                                            }
                                                        })
                                                        .map(saved -> BuyResponse.builder()
                                                                .sessionId(sessionId)
                                                                .fsItemId(item.getId())
                                                                .skuCode(item.getSkuCode())
                                                                .flashPrice(item.getFlashPrice())
                                                                .quantity(req.getQuantity())
                                                                .totalAmount(totalAmount)
                                                                .purchasedAt(LocalDateTime.now())
                                                                .build());
                                            });
                                });
                            });
                });
    }

    private Mono<Long> decrementStockInRedis(Long itemId, int quantity) {
        RedisScript<Long> script = RedisScript.of(LUA_DECR, Long.class);
        return redisTemplate.execute(script, Collections.singletonList("fs:stock:" + itemId), Collections.singletonList(String.valueOf(quantity)))
                .next();
    }

    private Mono<Long> getOrInitAndDecrementStock(FlashSaleItem item, int quantity) {
        String key = "fs:stock:" + item.getId();
        return decrementStockInRedis(item.getId(), quantity)
                .flatMap(result -> {
                    if (result == -1) {
                        int initialStock = item.getFlashStock() - item.getSoldQty();
                        return redisTemplate.opsForValue().set(key, String.valueOf(initialStock))
                                .then(decrementStockInRedis(item.getId(), quantity));
                    }
                    return Mono.just(result);
                });
    }

    private Mono<Map<String, Object>> fetchAddress(Long userId, Long addressId) {
        return Mono.create(sink -> {
            String correlationId = UUID.randomUUID().toString();
            CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
            pendingAddressRequests.put(correlationId, future);

            Map<String, Object> request = new LinkedHashMap<>();
            request.put("correlation_id", correlationId);
            request.put("user_id", userId);
            request.put("address_id", addressId);

            try {
                String payload = objectMapper.writeValueAsString(request);
                kafkaTemplate.send(KafkaTopics.ORDER_ADDRESS_REQUEST, correlationId, payload);

                future.orTimeout(5, TimeUnit.SECONDS)
                        .whenComplete((result, ex) -> {
                            pendingAddressRequests.remove(correlationId);
                            if (ex != null) {
                                sink.error(new AppException(ErrorCode.INTERNAL_ERROR, "Lấy thông tin địa chỉ thất bại hoặc timeout"));
                            } else {
                                if (Boolean.TRUE.equals(result.get("error"))) {
                                    sink.error(new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy địa chỉ"));
                                } else {
                                    sink.success(result);
                                }
                            }
                        });
            } catch (Exception e) {
                pendingAddressRequests.remove(correlationId);
                sink.error(e);
            }
        });
    }

    private Mono<Map<String, Object>> fetchVariantDetails(String skuCode) {
        return webClient.get()
                .uri("/v1/products/variants/sku/{skuCode}", skuCode)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {})
                .map(ApiResponse::getData)
                .onErrorMap(ex -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy thông tin SKU sản phẩm"));
    }

    private void publishCheckoutSubmittedEvent(Long userId, FlashSaleItem item, int quantity, BigDecimal totalAmount, Map<String, Object> addressInfo, Map<String, Object> variantInfo) {
        try {
            Map<String, Object> addrMap = new LinkedHashMap<>();
            addrMap.put("address_id", addressInfo.get("addressId"));
            addrMap.put("province_id", addressInfo.get("provinceId"));
            addrMap.put("district_id", addressInfo.get("districtId"));
            addrMap.put("full_address", addressInfo.get("fullAddress"));
            String addressSnapshot = toJson(addrMap);

            Map<String, Object> orderItem = new LinkedHashMap<>();
            orderItem.put("customer_id", userId);
            orderItem.put("variant_id", variantInfo.get("id"));
            orderItem.put("sku_code", item.getSkuCode());
            orderItem.put("product_name", variantInfo.get("productName"));
            orderItem.put("variant_name", variantInfo.get("variantName"));
            orderItem.put("price_snapshot", item.getFlashPrice());
            orderItem.put("quantity", quantity);
            orderItem.put("image_url", variantInfo.get("imageUrl"));
            orderItem.put("seller_id", variantInfo.get("sellerId"));
            orderItem.put("fs_item_id", item.getId());

            List<Map<String, Object>> orderItems = new ArrayList<>();
            orderItems.add(orderItem);

            String orderCheckoutSessionId = UUID.randomUUID().toString();

            Map<String, Object> event = new LinkedHashMap<>();
            event.put("event_id", "evt_" + System.currentTimeMillis());
            event.put("event_type", "order.checkout_submitted");
            event.put("timestamp", Instant.now().toString());
            event.put("session_id", orderCheckoutSessionId);
            event.put("customer_id", userId);
            event.put("items", orderItems);
            event.put("total_amount", totalAmount);
            event.put("total_items", quantity);
            event.put("address_snapshot", addressSnapshot);

            kafkaTemplate.send(KafkaTopics.ORDER_CHECKOUT_SUBMITTED,
                    String.valueOf(userId), toJson(event));
            log.info("Published order.checkout_submitted event for flash sale item: itemId={}, sessionId={}", item.getId(), orderCheckoutSessionId);
        } catch (Exception e) {
            log.error("Failed to publish order.checkout_submitted event", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
