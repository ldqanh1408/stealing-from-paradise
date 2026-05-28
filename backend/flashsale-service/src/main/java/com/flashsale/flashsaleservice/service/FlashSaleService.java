package com.flashsale.flashsaleservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.flashsaleservice.domain.model.FlashSaleItem;
import com.flashsale.flashsaleservice.domain.model.FlashSaleReminder;
import com.flashsale.flashsaleservice.domain.model.FlashSaleSession;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleItemRepository;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleReminderRepository;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleSessionRepository;
import com.flashsale.flashsaleservice.dto.request.*;
import com.flashsale.flashsaleservice.dto.response.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class FlashSaleService {

    private final FlashSaleSessionRepository sessionRepo;
    private final FlashSaleItemRepository itemRepo;
    private final FlashSaleReminderRepository reminderRepo;
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

    public FlashSaleService(
            FlashSaleSessionRepository sessionRepo,
            FlashSaleItemRepository itemRepo,
            FlashSaleReminderRepository reminderRepo,
            ReactiveStringRedisTemplate redisTemplate,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${product-service.url:http://localhost:8084}") String productServiceUrl) {
        this.sessionRepo = sessionRepo;
        this.itemRepo = itemRepo;
        this.reminderRepo = reminderRepo;
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder().baseUrl(productServiceUrl).build();
    }

    // ─── Kafka Listeners ────────────────────────────────────────────────────

    @KafkaListener(topics = KafkaTopics.FLASH_SALE_SESSION_STARTED, groupId = "flashsale-service-group")
    public void onSessionStarted(String sessionId) {
        log.info("Flash sale session started: {}", sessionId);
    }

    // ─── Public: List sessions ──────────────────────────────────────────────

    public Mono<SessionListResponse> getSessions(String status) {
        Flux<FlashSaleSession> sessionsFlux = (status != null && !status.isEmpty())
                ? sessionRepo.findByStatus(status)
                : sessionRepo.findAll();

        return sessionsFlux
                .map(this::toSessionResponse)
                .collectList()
                .map(sessions -> {
                    long now = Instant.now().toEpochMilli();
                    return SessionListResponse.builder()
                            .serverTime(now)
                            .sessions(sessions)
                            .build();
                });
    }

    public Mono<SessionDetailResponse> getSessionDetail(Long sessionId) {
        return sessionRepo.findById(sessionId)
                .flatMap(session ->
                        itemRepo.findBySessionId(sessionId)
                                .map(this::toItemResponse)
                                .collectList()
                                .map(items -> SessionDetailResponse.builder()
                                        .session(toSessionResponse(session))
                                        .items(items)
                                        .build())
                );
    }

    // ─── Seller: Add item to session ────────────────────────────────────────

    public Mono<FlashSaleItemResponse> createFlashSaleItem(Long sessionId, CreateFlashSaleItemRequest req) {
        FlashSaleItem item = FlashSaleItem.builder()
                .sessionId(sessionId)
                .skuCode(req.getSkuCode())
                .flashPrice(req.getFlashPrice())
                .flashStock(req.getFlashStock())
                .limitPerUser(req.getLimitPerUser() != null ? req.getLimitPerUser() : 1)
                .soldQty(0)
                .status("PENDING")
                .build();
        return itemRepo.save(item).map(this::toItemResponse);
    }

    // ─── Admin: Session management ──────────────────────────────────────────

    public Mono<SessionResponse> createSession(CreateSessionRequest req) {
        FlashSaleSession session = FlashSaleSession.builder()
                .name(req.getName())
                .startTime(req.getStartTime())
                .endTime(req.getEndTime())
                .status("UPCOMING")
                .build();
        return sessionRepo.save(session).map(this::toSessionResponse);
    }

    public Flux<SessionResponse> getAdminSessions(String status, int page, int size) {
        Flux<FlashSaleSession> sessionsFlux = (status != null && !status.isEmpty())
                ? sessionRepo.findByStatus(status)
                : sessionRepo.findAll();

        return sessionsFlux
                .skip((long) page * size)
                .take(size)
                .map(this::toSessionResponse);
    }

    public Mono<SessionResponse> updateSession(Long sessionId, UpdateSessionRequest req) {
        return sessionRepo.findById(sessionId)
                .flatMap(session -> {
                    if (req.getName() != null) session.setName(req.getName());
                    if (req.getStartTime() != null) session.setStartTime(req.getStartTime());
                    if (req.getEndTime() != null) session.setEndTime(req.getEndTime());
                    return sessionRepo.save(session);
                })
                .map(this::toSessionResponse);
    }

    public Mono<Void> deleteSession(Long sessionId) {
        return sessionRepo.findById(sessionId)
                .flatMap(session -> {
                    session.setDeletedAt(LocalDateTime.now());
                    return sessionRepo.save(session);
                })
                .then();
    }

    public Mono<FlashSaleItemResponse> approveItem(Long sessionId, Long itemId, ApproveItemRequest req) {
        return itemRepo.findById(itemId)
                .flatMap(item -> {
                    item.setStatus("APPROVED");
                    return itemRepo.save(item);
                })
                .map(this::toItemResponse);
    }

    public Mono<FlashSaleItemResponse> rejectItem(Long itemId, RejectItemRequest req) {
        return itemRepo.findById(itemId)
                .flatMap(item -> {
                    item.setStatus("REJECTED");
                    return itemRepo.save(item);
                })
                .map(this::toItemResponse);
    }

    // ─── Buyer: Purchase ────────────────────────────────────────────────────

    @KafkaListener(topics = KafkaTopics.ORDER_ADDRESS_RESPONSE, groupId = "flashsale-service-address-group")
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

    // ─── Buyer: Reminders ───────────────────────────────────────────────────

    public Mono<Void> setReminder(Long sessionId, Long userId) {
        return reminderRepo.findByCustomerIdAndSessionId(userId, sessionId)
                .switchIfEmpty(Mono.defer(() -> reminderRepo.save(
                        FlashSaleReminder.builder()
                                .customerId(userId)
                                .sessionId(sessionId)
                                .build()
                )))
                .then();
    }

    public Mono<Void> removeReminder(Long sessionId, Long userId) {
        return reminderRepo.findByCustomerIdAndSessionId(userId, sessionId)
                .flatMap(reminderRepo::delete)
                .then();
    }

    // ─── Mappers ────────────────────────────────────────────────────────────

    private SessionResponse toSessionResponse(FlashSaleSession s) {
        long secondsRemaining = 0;
        boolean isEnded = false;
        if ("ACTIVE".equals(s.getStatus())) {
            Duration d = Duration.between(LocalDateTime.now(), s.getEndTime());
            secondsRemaining = Math.max(0, d.getSeconds());
            isEnded = secondsRemaining <= 0;
        } else if (s.getEndTime() != null && s.getEndTime().isBefore(LocalDateTime.now())) {
            isEnded = true;
        }
        return SessionResponse.builder()
                .sessionId(s.getId())
                .name(s.getName())
                .status(s.getStatus())
                .startTime(s.getStartTime())
                .endTime(s.getEndTime())
                .secondsRemaining(secondsRemaining)
                .isEnded(isEnded)
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    private FlashSaleItemResponse toItemResponse(FlashSaleItem i) {
        return FlashSaleItemResponse.builder()
                .id(i.getId())
                .sessionId(i.getSessionId())
                .skuCode(i.getSkuCode())
                .flashPrice(i.getFlashPrice())
                .flashStock(i.getFlashStock())
                .limitPerUser(i.getLimitPerUser())
                .soldQty(i.getSoldQty())
                .status(i.getStatus())
                .createdAt(i.getCreatedAt())
                .updatedAt(i.getUpdatedAt())
                .build();
    }
}
