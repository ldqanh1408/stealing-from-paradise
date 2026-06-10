package com.flashsale.flashsaleservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.flashsaleservice.domain.model.FlashSaleSession;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleItemRepository;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleSessionRepository;
import com.flashsale.flashsaleservice.dto.request.CreateSessionRequest;
import com.flashsale.flashsaleservice.dto.request.UpdateSessionRequest;
import com.flashsale.flashsaleservice.dto.response.SessionDetailResponse;
import com.flashsale.flashsaleservice.dto.response.SessionListResponse;
import com.flashsale.flashsaleservice.dto.response.SessionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlashSaleSessionService {

    private final FlashSaleSessionRepository sessionRepo;
    private final FlashSaleItemRepository itemRepo;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final FlashSaleItemMapper itemMapper;

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
                                .map(itemMapper::toItemResponse)
                                .collectList()
                                .map(items -> SessionDetailResponse.builder()
                                        .session(toSessionResponse(session))
                                        .items(items)
                                        .build())
                );
    }

    public Mono<SessionListResponse> getActiveSessions() {
        return getSessions("ACTIVE");
    }

    public Mono<SessionResponse> createSession(CreateSessionRequest req) {
        FlashSaleSession session = FlashSaleSession.builder()
                .name(req.getName())
                .startTime(req.getStartTime())
                .endTime(req.getEndTime())
                .status("UPCOMING")
                .build();
        return sessionRepo.save(session)
                .flatMap(saved -> registerSessionTriggers(saved).thenReturn(saved))
                .doOnSuccess(this::publishSessionCreatedEvent)
                .map(this::toSessionResponse);
    }

    private Mono<Void> registerSessionTriggers(FlashSaleSession session) {
        String key = "flash_sale:triggers";
        Mono<Boolean> startTrigger = Mono.just(false);
        Mono<Boolean> endTrigger = Mono.just(false);

        if (session.getStartTime() != null) {
            double score = session.getStartTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            startTrigger = redisTemplate.opsForZSet().add(key, "start:" + session.getId(), score);
        }
        if (session.getEndTime() != null) {
            double score = session.getEndTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            endTrigger = redisTemplate.opsForZSet().add(key, "end:" + session.getId(), score);
        }

        return Mono.when(startTrigger, endTrigger)
                .doOnError(e -> log.warn("Failed to register Redis ZSET triggers for flashSaleSessionId={}: {}",
                        session.getId(), e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    private void publishSessionCreatedEvent(FlashSaleSession session) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("event_id", "evt_" + System.currentTimeMillis() + "_" + session.getId());
            event.put("event_type", KafkaTopics.FLASH_SALE_SESSION_CREATED);
            event.put("session_id", session.getId());
            event.put("name", session.getName());
            event.put("status", session.getStatus());
            event.put("start_time", session.getStartTime() != null ? session.getStartTime().toString() : null);
            event.put("end_time", session.getEndTime() != null ? session.getEndTime().toString() : null);
            event.put("timestamp", Instant.now().toString());
            kafkaTemplate.send(KafkaTopics.FLASH_SALE_SESSION_CREATED,
                    String.valueOf(session.getId()), objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Failed to publish session created event", e);
        }
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
                    if (req.getStatus() != null) session.setStatus(req.getStatus());
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

    public SessionResponse toSessionResponse(FlashSaleSession s) {
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
}
