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
public class PaymentEventConsumer {

    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.PAYMENT_SUCCESS, groupId = "notification-service-payment")
    public void onPaymentSuccess(String message) {
        createAndEmit(message, "PAYMENT_SUCCESS", "Thanh toán thành công",
                "Thanh toán cho đơn hàng của bạn đã thành công");
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_FAILED, groupId = "notification-service-payment")
    public void onPaymentFailed(String message) {
        createAndEmit(message, "PAYMENT_FAILED", "Thanh toán thất bại",
                "Thanh toán cho đơn hàng của bạn không thành công. Vui lòng thử lại.");
    }

    @KafkaListener(topics = KafkaTopics.REFUND_REQUESTED, groupId = "notification-service-payment")
    public void onRefundRequested(String message) {
        createAndEmit(message, "REFUND_REQUESTED", "Yêu cầu hoàn tiền",
                "Yêu cầu hoàn tiền của bạn đang được xem xét");
    }

    @KafkaListener(topics = KafkaTopics.REFUND_ADMIN_APPROVED, groupId = "notification-service-payment")
    public void onRefundAdminApproved(String message) {
        createAndEmit(message, "REFUND_APPROVED", "Hoàn tiền được duyệt",
                "Yêu cầu hoàn tiền của bạn đã được phê duyệt");
    }

    @KafkaListener(topics = KafkaTopics.REFUND_REJECTED, groupId = "notification-service-payment")
    public void onRefundRejected(String message) {
        createAndEmit(message, "REFUND_REJECTED", "Hoàn tiền bị từ chối",
                "Yêu cầu hoàn tiền của bạn đã bị từ chối");
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
        for (String key : new String[]{"user_id", "userId", "buyer_id", "customer_id", "seller_id"}) {
            Object val = event.get(key);
            if (val instanceof Number n) return n.longValue();
            if (val instanceof String s) {
                try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }
}
