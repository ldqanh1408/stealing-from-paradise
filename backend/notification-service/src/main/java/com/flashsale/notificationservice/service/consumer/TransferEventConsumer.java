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
public class TransferEventConsumer {

    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.SELLER_TRANSFER_ELIGIBLE, groupId = "notification-service-transfer")
    public void onTransferEligible(String message) {
        createAndEmit(message, "TRANSFER_ELIGIBLE", "Thanh toán sắp được giải ngân",
                "Khoản thanh toán của bạn sắp được chuyển về tài khoản");
    }

    @KafkaListener(topics = KafkaTopics.SELLER_TRANSFER_PAID_OUT, groupId = "notification-service-transfer")
    public void onTransferPaidOut(String message) {
        createAndEmit(message, "TRANSFER_PAID_OUT", "Thanh toán đã được giải ngân",
                "Khoản thanh toán đã được chuyển vào tài khoản Stripe của bạn");
    }

    @KafkaListener(topics = KafkaTopics.SELLER_TRANSFER_FAILED, groupId = "notification-service-transfer")
    public void onTransferFailed(String message) {
        createAndEmit(message, "TRANSFER_FAILED", "Giải ngân thất bại",
                "Khoản giải ngân của bạn gặp lỗi. Đội ngũ kỹ thuật sẽ xử lý.");
    }

    @KafkaListener(topics = KafkaTopics.STOCK_RESERVATION_EXPIRED, groupId = "notification-service-transfer")
    public void onStockReservationExpired(String message) {
        createAndEmit(message, "STOCK_RESERVATION_EXPIRED", "Đặt hàng hết hạn",
                "Thời gian giữ hàng đã hết. Đơn hàng của bạn đã bị hủy.");
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
        for (String key : new String[]{"seller_id", "sellerId", "user_id", "userId", "buyer_id", "customer_id"}) {
            Object val = event.get(key);
            if (val instanceof Number n) return n.longValue();
            if (val instanceof String s) {
                try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }
}
