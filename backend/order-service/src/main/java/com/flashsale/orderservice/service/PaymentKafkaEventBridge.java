package com.flashsale.orderservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.orderservice.axon.event.ParentOrderPaymentFailedEvent;
import com.flashsale.orderservice.axon.event.ParentOrderPaymentSucceededEvent;
import com.flashsale.orderservice.domain.model.ParentOrder;
import com.flashsale.orderservice.domain.repository.ParentOrderRepository;
import com.flashsale.orderservice.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.eventhandling.gateway.EventGateway;
import com.flashsale.commonlib.infra.outbox.OutboxEventWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentKafkaEventBridge {

    private final ObjectMapper objectMapper;
    private final EventGateway eventGateway;
    private final OrderRepository orderRepository;
    private final ParentOrderRepository parentOrderRepository;
    private final OutboxEventWriter outboxEventWriter;

    public void onPaymentSuccess(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            Long parentOrderId = toLong(payload.get("parent_order_id"));
            if (parentOrderId == null) {
                log.warn("Ignore payment.success without parent_order_id");
                return;
            }

            eventGateway.publish(new ParentOrderPaymentSucceededEvent(parentOrderId));

            publishOrderPaidEvent(parentOrderId);
        } catch (Exception e) {
            log.error("Error bridging payment.success to Axon event: {}", e.getMessage(), e);
        }
    }

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

            publishOrderPaymentFailedEvent(parentOrderId, reason);
        } catch (Exception e) {
            log.error("Error bridging payment.failed to Axon event: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public void publishOrderPaidEvent(Long parentOrderId) {
        Optional<ParentOrder> parentOpt = parentOrderRepository.findById(parentOrderId);
        if (parentOpt.isEmpty()) {
            log.warn("ParentOrder {} not found for order.paid event", parentOrderId);
            return;
        }

        ParentOrder parent = parentOpt.get();
        String sessionId = parent.getSessionId();

        Map<String, Object> event = Map.of(
                "event_id", "evt_" + System.currentTimeMillis(),
                "event_type", "order.paid",
                "parent_order_id", parentOrderId,
                "session_id", sessionId != null ? sessionId : "",
                "customer_id", parent.getCustomerId(),
                "total_amount", parent.getTotalAmt(),
                "timestamp", java.time.Instant.now().toString()
        );

        try {
            outboxEventWriter.append("order", String.valueOf(parentOrderId), KafkaTopics.ORDER_PAID,
                    KafkaTopics.ORDER_PAID, String.valueOf(parentOrderId), event);
            log.info("Published order.paid to outbox: parentOrderId={}, sessionId={}", parentOrderId, sessionId);
        } catch (Exception e) {
            log.error("Failed to publish order.paid event: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public void publishOrderPaymentFailedEvent(Long parentOrderId, String reason) {
        Optional<ParentOrder> parentOpt = parentOrderRepository.findById(parentOrderId);
        if (parentOpt.isEmpty()) {
            log.warn("ParentOrder {} not found for order.payment_failed event", parentOrderId);
            return;
        }

        ParentOrder parent = parentOpt.get();
        String sessionId = parent.getSessionId();

        Map<String, Object> event = Map.of(
                "event_id", "evt_" + System.currentTimeMillis(),
                "event_type", "order.payment_failed",
                "parent_order_id", parentOrderId,
                "session_id", sessionId != null ? sessionId : "",
                "customer_id", parent.getCustomerId(),
                "reason", reason,
                "timestamp", java.time.Instant.now().toString()
        );

        try {
            outboxEventWriter.append("order", String.valueOf(parentOrderId), KafkaTopics.ORDER_PAYMENT_FAILED,
                    KafkaTopics.ORDER_PAYMENT_FAILED, String.valueOf(parentOrderId), event);
            log.info("Published order.payment_failed to outbox: parentOrderId={}, sessionId={}", parentOrderId, sessionId);
        } catch (Exception e) {
            log.error("Failed to publish order.payment_failed event: {}", e.getMessage(), e);
        }
    }

    /**
     * Nhận refund.admin_approved từ payment-service.
     * Cập nhật order.status:
     *   - PARTIAL refund → PARTIALLY_REFUNDED
     *   - FULL refund    → REFUNDED
     */
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
