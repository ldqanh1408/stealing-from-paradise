package com.flashsale.notificationservice.service.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.notificationservice.domain.model.Notification;
import com.flashsale.notificationservice.domain.repository.NotificationRepository;
import com.flashsale.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class FlashSaleEventConsumer {

    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.FLASH_SALE_SESSION_STARTED, groupId = "notification-service-flashsale")
    public void onSessionStarted(String message) {
        broadcastNotification(message, "FLASH_SALE_STARTED", "Flash Sale bắt đầu",
                "Flash Sale đã bắt đầu! Nhanh tay săn deal ngay!");
    }

    @KafkaListener(topics = KafkaTopics.FLASH_SALE_SESSION_ENDED, groupId = "notification-service-flashsale")
    public void onSessionEnded(String message) {
        broadcastNotification(message, "FLASH_SALE_ENDED", "Flash Sale kết thúc",
                "Flash Sale đã kết thúc. Cảm ơn bạn đã tham gia!");
    }

    @KafkaListener(topics = KafkaTopics.FLASH_SALE_ITEM_PURCHASED, groupId = "notification-service-flashsale")
    public void onItemPurchased(String message) {
        createAndEmit(message, "FLASH_SALE_PURCHASED", "Mua Flash Sale thành công",
                "Bạn đã mua thành công sản phẩm Flash Sale!");
    }

    @SuppressWarnings("unchecked")
    private void createAndEmit(String message, String type, String title, String body) {
        try {
            Map<String, Object> event = objectMapper.readValue(message, Map.class);
            Long userId = extractUserId(event);
            if (userId == null) return;

            Notification notif = Notification.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(userId)
                    .type(type)
                    .title(title)
                    .body(body)
                    .metadata(message)
                    .isRead(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            notificationRepository.save(notif).subscribe(
                    saved -> notificationService.emitToUser(saved),
                    err -> log.error("Failed to save notification: {}", err.getMessage())
            );
        } catch (Exception e) {
            log.error("Failed to process {} event: {}", type, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void broadcastNotification(String message, String type, String title, String body) {
        try {
            Map<String, Object> event = objectMapper.readValue(message, Map.class);
            Long userId = extractUserId(event);
            if (userId != null) {
                createAndEmit(message, type, title, body);
            }
        } catch (Exception e) {
            log.error("Failed to process broadcast event {}: {}", type, e.getMessage());
        }
    }

    private Long extractUserId(Map<String, Object> event) {
        for (String key : new String[]{"user_id", "userId", "customer_id", "buyer_id"}) {
            Object val = event.get(key);
            if (val instanceof Number n) return n.longValue();
            if (val instanceof String s) {
                try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }
}
