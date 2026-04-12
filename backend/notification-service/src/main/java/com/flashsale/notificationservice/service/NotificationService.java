package com.flashsale.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.event.payload.OrderDeliveredPayload;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    @KafkaListener(topics = KafkaTopics.ORDER_DELIVERED, groupId = "notification-service-group")
    public void onOrderDelivered(OrderDeliveredPayload payload) {
        log.info("Order delivered notification: orderId={}", payload.getOrderId());
        // TODO: Send SSE to buyer
    }

    public void sendNotification(String userId, String message) {
        log.info("Sending notification to user: {}", userId);
        // TODO: Implement SSE push
    }
}

