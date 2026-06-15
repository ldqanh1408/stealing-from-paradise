package com.flashsale.flashsaleservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleItemRepository;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleReminderRepository;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleSessionRepository;
import com.flashsale.flashsaleservice.dto.request.*;
import com.flashsale.flashsaleservice.dto.response.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class FlashSaleService {

    private final FlashSaleSessionService sessionService;
    private final FlashSaleItemService itemService;
    private final FlashSaleReminderService reminderService;
    private final FlashSalePurchaseService purchaseService;

    @Autowired
    public FlashSaleService(
            FlashSaleSessionService sessionService,
            FlashSaleItemService itemService,
            FlashSaleReminderService reminderService,
            FlashSalePurchaseService purchaseService) {
        this.sessionService = sessionService;
        this.itemService = itemService;
        this.reminderService = reminderService;
        this.purchaseService = purchaseService;
    }

    // Legacy constructor for unit tests
    public FlashSaleService(
            FlashSaleSessionRepository sessionRepo,
            FlashSaleItemRepository itemRepo,
            FlashSaleReminderRepository reminderRepo,
            ReactiveStringRedisTemplate redisTemplate,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            String productServiceUrl) {
        FlashSaleItemMapper mapper = new FlashSaleItemMapper();
        this.sessionService = new FlashSaleSessionService(sessionRepo, itemRepo, redisTemplate, kafkaTemplate, objectMapper, mapper, productServiceUrl);
        this.itemService = new FlashSaleItemService(itemRepo, kafkaTemplate, objectMapper, mapper);
        this.reminderService = new FlashSaleReminderService(reminderRepo);
        this.purchaseService = new FlashSalePurchaseService(sessionRepo, itemRepo, redisTemplate, kafkaTemplate, objectMapper, productServiceUrl);
    }

    public Mono<SessionListResponse> getSessions(String status) {
        return sessionService.getSessions(status);
    }

    public Mono<SessionDetailResponse> getSessionDetail(Long sessionId) {
        return sessionService.getSessionDetail(sessionId);
    }

    public Mono<SessionListResponse> getActiveSessions() {
        return sessionService.getActiveSessions();
    }

    public Mono<FlashSaleItemResponse> createFlashSaleItem(Long sessionId, Long sellerId, CreateFlashSaleItemRequest req) {
        return itemService.createFlashSaleItem(sessionId, sellerId, req);
    }

    public Mono<SessionResponse> createSession(CreateSessionRequest req) {
        return sessionService.createSession(req);
    }

    public Flux<SessionResponse> getAdminSessions(String status, int page, int size) {
        return sessionService.getAdminSessions(status, page, size);
    }

    public Mono<SessionResponse> updateSession(Long sessionId, UpdateSessionRequest req) {
        return sessionService.updateSession(sessionId, req);
    }

    public Mono<Void> deleteSession(Long sessionId) {
        return sessionService.deleteSession(sessionId);
    }

    public Mono<FlashSaleItemResponse> approveItem(Long sessionId, Long itemId, ApproveItemRequest req) {
        return itemService.approveItem(sessionId, itemId, req);
    }

    public Mono<FlashSaleItemResponse> rejectItem(Long sessionId, Long itemId, RejectItemRequest req) {
        return itemService.rejectItem(sessionId, itemId, req);
    }

    public void onAddressResponse(String message) {
        purchaseService.onAddressResponse(message);
    }

    public Mono<BuyResponse> buyFlashSaleItem(Long sessionId, Long userId, BuyRequest req) {
        return purchaseService.buyFlashSaleItem(sessionId, userId, req);
    }

    public Mono<Void> setReminder(Long sessionId, Long userId) {
        return reminderService.setReminder(sessionId, userId);
    }

    public Mono<Void> removeReminder(Long sessionId, Long userId) {
        return reminderService.removeReminder(sessionId, userId);
    }
}
