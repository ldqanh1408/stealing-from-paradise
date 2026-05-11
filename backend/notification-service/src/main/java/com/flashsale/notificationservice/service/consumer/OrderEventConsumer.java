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
public class OrderEventConsumer {

    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.ORDER_CREATED, groupId = "notification-service-order")
    public void onOrderCreated(String message) {
        createAndEmit(message, "ORDER_CREATED", "Đơn hàng mới",
                "Đơn hàng của bạn đã được tạo thành công");
    }

    @KafkaListener(topics = KafkaTopics.ORDER_SHIPPED, groupId = "notification-service-order")
    public void onOrderShipped(String message) {
        createAndEmit(message, "ORDER_SHIPPED", "Đơn hàng đang giao",
                "Đơn hàng của bạn đang được vận chuyển");
    }

    @KafkaListener(topics = KafkaTopics.ORDER_DELIVERED, groupId = "notification-service-order")
    public void onOrderDelivered(String message) {
        createAndEmit(message, "ORDER_DELIVERED", "Giao hàng thành công",
                "Đơn hàng của bạn đã được giao thành công");
    }

    @KafkaListener(topics = KafkaTopics.ORDER_CANCELLED, groupId = "notification-service-order")
    public void onOrderCancelled(String message) {
        createAndEmit(message, "ORDER_CANCELLED", "Đơn hàng bị hủy",
                "Đơn hàng của bạn đã bị hủy");
    }

    @KafkaListener(topics = KafkaTopics.ORDER_RETURNED_RTS, groupId = "notification-service-order")
    public void onOrderReturned(String message) {
        createAndEmit(message, "ORDER_RETURNED", "Đơn hàng hoàn trả",
                "Đơn hàng đã được hoàn trả về người bán");
    }

    @KafkaListener(topics = KafkaTopics.SELLER_ORDER_CANCELLED, groupId = "notification-service-order")
    public void onSellerOrderCancelled(String message) {
        createAndEmit(message, "SELLER_ORDER_CANCELLED", "Người bán hủy đơn",
                "Người bán đã hủy đơn hàng của bạn. Tiền sẽ được hoàn lại.");
    }

    @KafkaListener(topics = KafkaTopics.ORDER_PAYMENT_TIMEOUT, groupId = "notification-service-order")
    public void onPaymentTimeout(String message) {
        createAndEmit(message, "ORDER_PAYMENT_TIMEOUT", "Hết thời gian thanh toán",
                "Đơn hàng bị hủy do hết thời gian thanh toán");
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
                    err -> log.error("Failed to save notification type={}: {}", type, err.getMessage())
            );
        } catch (Exception e) {
            log.error("Failed to process event {}: {}", type, e.getMessage());
        }
    }

    private Long extractUserId(Map<String, Object> event) {
        for (String key : new String[]{"user_id", "userId", "buyer_id", "customer_id"}) {
            Object val = event.get(key);
            if (val instanceof Number n) return n.longValue();
            if (val instanceof String s) {
                try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
            }
        }
        log.warn("Could not extract userId from event: {}", event);
        return null;
    }
}
