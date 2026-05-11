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
public class ProductEventConsumer {

    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.PRODUCT_PENDING_REVIEW, groupId = "notification-service-product")
    public void onProductPendingReview(String message) {
        createAndEmit(message, "PRODUCT_PENDING_REVIEW", "Sản phẩm chờ duyệt",
                "Sản phẩm mới đang chờ được xem xét");
    }

    @KafkaListener(topics = KafkaTopics.PRODUCT_APPROVED, groupId = "notification-service-product")
    public void onProductApproved(String message) {
        createAndEmit(message, "PRODUCT_APPROVED", "Sản phẩm được duyệt",
                "Sản phẩm của bạn đã được phê duyệt và hiển thị trên cửa hàng");
    }

    @KafkaListener(topics = KafkaTopics.PRODUCT_REJECTED, groupId = "notification-service-product")
    public void onProductRejected(String message) {
        createAndEmit(message, "PRODUCT_REJECTED", "Sản phẩm bị từ chối",
                "Sản phẩm của bạn đã bị từ chối. Vui lòng kiểm tra lý do.");
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

    private Long extractUserId(Map<String, Object> event) {
        for (String key : new String[]{"seller_id", "sellerId", "user_id", "userId"}) {
            Object val = event.get(key);
            if (val instanceof Number n) return n.longValue();
            if (val instanceof String s) {
                try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }
}
