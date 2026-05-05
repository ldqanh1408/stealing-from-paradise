package com.flashsale.orderservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.orderservice.axon.event.ParentOrderPaymentFailedEvent;
import com.flashsale.orderservice.axon.event.ParentOrderPaymentSucceededEvent;
import com.flashsale.orderservice.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentKafkaEventBridge {

    private final ObjectMapper objectMapper;
    private final EventGateway eventGateway;
    private final OrderRepository orderRepository;

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

    /**
     * Nhận refund.admin_approved từ payment-service.
     * Cập nhật order.status:
     *   - PARTIAL refund → PARTIALLY_REFUNDED
     *   - FULL refund    → REFUNDED
     */
    @KafkaListener(topics = KafkaTopics.REFUND_ADMIN_APPROVED, groupId = "order-service-group")
    @Transactional
    public void onRefundApproved(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            Long orderId = toLong(payload.get("order_id"));
            String type  = payload.get("type") != null ? payload.get("type").toString() : "PARTIAL";
            if (orderId == null) {
                log.warn("onRefundApproved: missing order_id");
                return;
            }

            String newStatus = "FULL".equals(type) ? "REFUNDED" : "PARTIALLY_REFUNDED";

            orderRepository.findById(orderId).ifPresent(order -> {
                // Idempotency: bỏ qua nếu đơn đã ở trạng thái đích
                if (newStatus.equals(order.getStatus())) return;
                order.setStatus(newStatus);
                orderRepository.save(order);
                log.info("Order status updated after refund approved: orderId={}, type={}, newStatus={}",
                        orderId, type, newStatus);
            });
        } catch (Exception e) {
            log.error("Error processing refund.admin_approved: {}", e.getMessage(), e);
        }
    }

    /**
     * Nhận refund.rts_completed từ payment-service.
     * Order đã ở RETURNED — chỉ log để xác nhận Stripe refund đã thực thi.
     */
    @KafkaListener(topics = KafkaTopics.REFUND_RTS_COMPLETED, groupId = "order-service-group")
    public void onRefundRtsCompleted(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            Long orderId      = toLong(payload.get("order_id"));
            String refundStatus = payload.get("status") != null ? payload.get("status").toString() : "";
            log.info("RTS refund completed: orderId={}, refundStatus={}", orderId, refundStatus);
        } catch (Exception e) {
            log.error("Error processing refund.rts_completed: {}", e.getMessage(), e);
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

