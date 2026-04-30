package com.flashsale.notificationservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.event.payload.OrderDeliveredPayload;
import com.flashsale.commonlib.event.payload.SellerStripeRequirementPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.ORDER_DELIVERED, groupId = "notification-service-group")
    public void onOrderDelivered(String message) {
        try {
            OrderDeliveredPayload payload = objectMapper.readValue(message, OrderDeliveredPayload.class);
            log.info("Order delivered notification: orderId={}", payload.getOrderId());
            // TODO: Send SSE to buyer
        } catch (Exception e) {
            log.error("Failed to process order.delivered event: {}", e.getMessage(), e);
        }
    }

    /**
     * Nhận event khi seller cần hoàn tất yêu cầu Stripe (requirements).
     * Gửi notification cho seller kèm link để hoàn tất.
     */
    @KafkaListener(topics = KafkaTopics.SELLER_STRIPE_REQUIREMENT, groupId = "notification-service-group")
    public void onSellerStripeRequirement(String message) {
        try {
            SellerStripeRequirementPayload payload = objectMapper.readValue(message, SellerStripeRequirementPayload.class);
            log.info("Seller Stripe requirement notification: sellerId={}, type={}, reason={}",
                    payload.getSellerId(), payload.getRequirementType(), payload.getRequirementReason());
            // TODO: Send SSE / email / push notification to seller
        } catch (Exception e) {
            log.error("Failed to process seller.stripe.requirement event: {}", e.getMessage(), e);
        }
    }

    public void sendNotification(String userId, String message) {
        log.info("Sending notification to user: {}", userId);
        // TODO: Implement SSE push
    }
}

