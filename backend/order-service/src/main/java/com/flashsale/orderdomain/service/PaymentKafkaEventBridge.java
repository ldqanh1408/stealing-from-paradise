package com.flashsale.orderdomain.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.orderdomain.axon.event.ParentOrderPaymentFailedEvent;
import com.flashsale.orderdomain.axon.event.ParentOrderPaymentSucceededEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentKafkaEventBridge {

    private final ObjectMapper objectMapper;
    private final EventGateway eventGateway;

    @KafkaListener(topics = KafkaTopics.PAYMENT_SUCCESS, groupId = "order-service-group")
    public void onPaymentSuccess(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            Long parentOrderId = toLong(payload.get("parent_order_id"));
            if (parentOrderId == null) {
                log.warn("Ignore payment.success without parent_order_id");
                return;
            }
            eventGateway.publish(new ParentOrderPaymentSucceededEvent(parentOrderId));
        } catch (Exception e) {
            log.error("Error bridging payment.success to Axon event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_FAILED, groupId = "order-service-group")
    public void onPaymentFailed(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            Long parentOrderId = toLong(payload.get("parent_order_id"));
            if (parentOrderId == null) {
                log.warn("Ignore payment.failed without parent_order_id");
                return;
            }
            String reason = payload.get("reason") != null ? payload.get("reason").toString() : "Thanh toan that bai";
            eventGateway.publish(new ParentOrderPaymentFailedEvent(parentOrderId, reason));
        } catch (Exception e) {
            log.error("Error bridging payment.failed to Axon event: {}", e.getMessage(), e);
        }
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return null;
        }
    }
}

