package com.flashsale.identitydomain.service;

import com.flashsale.commonlib.event.payload.OrderDeliveredPayload;
import com.flashsale.commonlib.event.payload.BaseKafkaEvent;
import com.flashsale.identitydomain.service.LoyaltyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoyaltyKafkaConsumer {

    private final LoyaltyService loyaltyService;

    @KafkaListener(
            topics = "order.delivered",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderDelivered(
            @Payload OrderDeliveredPayload payload,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("Received order.delivered event: orderId={}, buyerId={}, amount={}, partition={}, offset={}",
                payload.getOrderId(), payload.getBuyerId(), payload.getTotalAmount(), partition, offset);

        try {
            Long buyerId = parseUserId(payload.getBuyerId());
            if (buyerId == null) {
                log.warn("Invalid buyerId in order.delivered event: {}", payload.getBuyerId());
                return;
            }

            loyaltyService.earnPoints(
                    buyerId,
                    payload.getOrderId(),
                    null,
                    BigDecimal.valueOf(payload.getTotalAmount())
            );

            log.info("Loyalty points earned for order {} by user {}", payload.getOrderId(), buyerId);
        } catch (Exception e) {
            log.error("Failed to process order.delivered for order {}: {}", payload.getOrderId(), e.getMessage(), e);
            throw e;
        }
    }

    private Long parseUserId(String userIdStr) {
        if (userIdStr == null || userIdStr.isBlank()) return null;
        try {
            return Long.parseLong(userIdStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
