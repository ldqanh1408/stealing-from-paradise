package com.flashsale.flashsaleservice.service;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.flashsaleservice.domain.model.FlashSaleItem;
import com.flashsale.flashsaleservice.domain.model.FlashSaleReminder;
import com.flashsale.flashsaleservice.domain.model.FlashSaleSession;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleItemRepository;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleReminderRepository;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleSessionRepository;
import com.flashsale.flashsaleservice.dto.request.*;
import com.flashsale.flashsaleservice.dto.response.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlashSaleService {

    private final FlashSaleSessionRepository sessionRepo;
    private final FlashSaleItemRepository itemRepo;
    private final FlashSaleReminderRepository reminderRepo;

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

    public Mono<BuyResponse> buyFlashSaleItem(Long sessionId, Long userId, BuyRequest req) {
        return itemRepo.findById(req.getFsItemId())
                .flatMap(item -> {
                    BigDecimal totalAmount = item.getFlashPrice().multiply(BigDecimal.valueOf(req.getQuantity()));

                    // TODO: Implement Redis stock decrement (Lua script) + Kafka order publish
                    log.info("User {} buying item {} from session {}: qty={}, total={}",
                            userId, req.getFsItemId(), sessionId, req.getQuantity(), totalAmount);

                    return Mono.just(BuyResponse.builder()
                            .sessionId(sessionId)
                            .fsItemId(item.getId())
                            .skuCode(item.getSkuCode())
                            .flashPrice(item.getFlashPrice())
                            .quantity(req.getQuantity())
                            .totalAmount(totalAmount)
                            .purchasedAt(LocalDateTime.now())
                            .build());
                });
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
