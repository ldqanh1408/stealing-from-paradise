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
public class ChatEventConsumer {

    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.AI_CHAT_MESSAGE_SENT, groupId = "notification-service-chat")
    public void onChatMessageSent(String message) {
        createAndEmit(message, "CHAT_MESSAGE", "Tin nhắn mới từ AI Assistant",
                "Bạn có tin nhắn mới từ trợ lý AI");
    }

    @KafkaListener(topics = KafkaTopics.AI_CHAT_TOOL_CALL_EXECUTED, groupId = "notification-service-chat")
    public void onToolCallExecuted(String message) {
        log.info("AI tool call executed: {}", message);
    }

    @KafkaListener(topics = KafkaTopics.AI_CHAT_CONFIRMATION_RESOLVED, groupId = "notification-service-chat")
    public void onConfirmationResolved(String message) {
        createAndEmit(message, "CONFIRMATION_RESOLVED", "Xác nhận hoàn tất",
                "Yêu cầu xác nhận của bạn đã được xử lý");
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
            log.error("Failed to process chat event: {}", e.getMessage());
        }
    }

    private Long extractUserId(Map<String, Object> event) {
        for (String key : new String[]{"user_id", "userId"}) {
            Object val = event.get(key);
            if (val instanceof Number n) return n.longValue();
            if (val instanceof String s) {
                try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }
}
