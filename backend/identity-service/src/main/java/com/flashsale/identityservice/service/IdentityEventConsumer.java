package com.flashsale.identityservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes domain events from other services.
 * Post-MVP: these listeners will trigger business actions
 * (e.g., unlock seller posting after delivery, notify buyer on refund rejection).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class IdentityEventConsumer {

    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.ORDER_DELIVERED, groupId = "identity-service-event-group")
    public void onOrderDelivered(String message) {
        // Post-MVP: unlock seller posting capability
        log.info("Order delivered event received: {}", message);
    }

    @KafkaListener(topics = KafkaTopics.ORDER_CANCELLED, groupId = "identity-service-event-group")
    public void onOrderCancelled(String message) {
        // Audit log for cancelled orders
        log.info("Order cancelled event received: {}", message);
    }

    @KafkaListener(topics = KafkaTopics.REFUND_ADMIN_APPROVED, groupId = "identity-service-event-group")
    public void onRefundAdminApproved(String message) {
        // Log refund approval
        log.info("Refund admin approved event received: {}", message);
    }

    @KafkaListener(topics = KafkaTopics.REFUND_REJECTED, groupId = "identity-service-event-group")
    public void onRefundRejected(String message) {
        // Post-MVP: notify buyer
        log.info("Refund rejected event received: {}", message);
    }
}
