package com.flashsale.flashsaleservice.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.flashsaleservice.domain.model.FlashSaleItem;
import com.flashsale.flashsaleservice.domain.model.FlashSaleSession;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleItemRepository;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JOB-01: drives FlashSaleSession lifecycle UPCOMING → ACTIVE → ENDED.
 * Without this scheduler nothing else in the flash-sale pricing chain fires.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FlashSaleSessionScheduler {

    private final FlashSaleSessionRepository sessionRepo;
    private final FlashSaleItemRepository itemRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${flashsale.session-scheduler.delay-ms:60000}")
    public void tick() {
        LocalDateTime now = LocalDateTime.now();

        sessionRepo.findByStatusAndStartTimeLessThanEqual("UPCOMING", now)
                .filter(s -> s.getDeletedAt() == null)
                .flatMap(this::activate)
                .doOnError(e -> log.error("FlashSaleSessionScheduler: activate error: {}", e.getMessage(), e))
                .subscribe();

        sessionRepo.findByStatusAndEndTimeLessThanEqual("ACTIVE", now)
                .filter(s -> s.getDeletedAt() == null)
                .flatMap(this::end)
                .doOnError(e -> log.error("FlashSaleSessionScheduler: end error: {}", e.getMessage(), e))
                .subscribe();
    }

    private Mono<FlashSaleSession> activate(FlashSaleSession session) {
        session.setStatus("ACTIVE");
        return sessionRepo.save(session)
                .flatMap(saved -> publish(KafkaTopics.FLASH_SALE_SESSION_STARTED, saved)
                        .thenReturn(saved))
                .doOnSuccess(saved -> {
                    log.info("Flash sale session ACTIVE: id={}, name={}", saved.getId(), saved.getName());
                });
    }

    private Mono<FlashSaleSession> end(FlashSaleSession session) {
        session.setStatus("ENDED");
        return sessionRepo.save(session)
                .flatMap(saved -> publish(KafkaTopics.FLASH_SALE_SESSION_ENDED, saved)
                        .thenReturn(saved))
                .doOnSuccess(saved -> {
                    log.info("Flash sale session ENDED: id={}, name={}", saved.getId(), saved.getName());
                });
    }

    private Mono<Void> publish(String topic, FlashSaleSession session) {
        return itemRepo.findBySessionId(session.getId())
                .filter(item -> "APPROVED".equals(item.getStatus()))
                .collectList()
                .doOnNext(items -> publishWithItems(topic, session, items))
                .then();
    }

    private void publishWithItems(String topic, FlashSaleSession session, List<FlashSaleItem> items) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("event_id", "evt_" + System.currentTimeMillis() + "_" + session.getId());
            event.put("event_type", topic);
            event.put("session_id", session.getId());
            event.put("sessionId", session.getId());
            event.put("name", session.getName());
            event.put("start_time", session.getStartTime() != null ? session.getStartTime().toString() : null);
            event.put("end_time", session.getEndTime() != null ? session.getEndTime().toString() : null);
            event.put("flashItems", toFlashItems(items));
            event.put("timestamp", Instant.now().toString());
            kafkaTemplate.send(topic, String.valueOf(session.getId()), objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Failed to publish {} for sessionId={}: {}", topic, session.getId(), e.getMessage(), e);
        }
    }

    private List<Map<String, Object>> toFlashItems(List<FlashSaleItem> items) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (FlashSaleItem item : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("fs_item_id", item.getId());
            row.put("sku_code", item.getSkuCode());
            row.put("flash_price", item.getFlashPrice());
            row.put("flash_stock", item.getFlashStock());
            out.add(row);
        }
        return out;
    }
}
