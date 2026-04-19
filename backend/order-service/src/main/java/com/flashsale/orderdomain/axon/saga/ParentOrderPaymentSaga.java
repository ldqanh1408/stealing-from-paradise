package com.flashsale.orderdomain.axon.saga;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.orderdomain.axon.event.*;
import com.flashsale.orderdomain.domain.model.Order;
import com.flashsale.orderdomain.domain.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Saga
@Slf4j
public class ParentOrderPaymentSaga {

    @Autowired
    private transient KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private transient ObjectMapper objectMapper;

    @Autowired
    private transient OrderRepository orderRepository;

    @Autowired
    private transient EventGateway eventGateway;

    @StartSaga
    @SagaEventHandler(associationProperty = "parentOrderId")
    public void on(ParentOrderCheckoutCreatedEvent event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("parent_order_id", event.getParentOrderId());
        payload.put("user_id", event.getUserId());
        payload.put("total_amount", event.getTotalAmount());
        payload.put("currency", "VND");
        payload.put("timeout_at", event.getTimeoutAt() != null ? event.getTimeoutAt().toString() : null);
        payload.put("timestamp", Instant.now().toString());

        send(KafkaTopics.PAYMENT_REQUESTED, String.valueOf(event.getParentOrderId()), payload);
        log.info("[ParentPaymentSaga][{}] Payment requested", event.getParentOrderId());
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "parentOrderId")
    public void on(ParentOrderPaymentSucceededEvent event) {
        List<Order> orders = orderRepository.findAllByParentOrderIdAndStatus(event.getParentOrderId(), "PENDING");
        orders.forEach(order -> {
            order.setStatus("PAID");
            orderRepository.save(order);
            eventGateway.publish(new OrderPaidEvent(
                    order.getId(),
                    order.getParentOrderId(),
                    order.getUserId(),
                    order.getSellerId(),
                    order.getFinalAmt()
            ));
        });

        log.info("[ParentPaymentSaga][{}] Payment succeeded, updated {} sub-orders", event.getParentOrderId(), orders.size());
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "parentOrderId")
    public void on(ParentOrderPaymentFailedEvent event) {
        String failReason = event.getReason() == null || event.getReason().isBlank()
                ? "Thanh toan that bai"
                : event.getReason();

        List<Order> orders = orderRepository.findAllByParentOrderIdAndStatus(event.getParentOrderId(), "PENDING");
        orders.forEach(order -> {
            order.setStatus("CANCELLED");
            order.setCancelledBy("SYSTEM");
            order.setCancelReason(failReason);
            order.setCancelledAt(LocalDateTime.now());
            orderRepository.save(order);

            eventGateway.publish(new OrderCancelledEvent(
                    order.getId(),
                    order.getParentOrderId(),
                    order.getUserId(),
                    order.getSellerId(),
                    "PAYMENT_FAILED",
                    failReason,
                    order.getTotalAmt()
            ));
        });

        log.info("[ParentPaymentSaga][{}] Payment failed, cancelled {} sub-orders", event.getParentOrderId(), orders.size());
    }

    private void send(String topic, String key, Map<String, Object> payload) {
        try {
            kafkaTemplate.send(topic, key, objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            log.error("[ParentPaymentSaga] Failed to serialize payload for topic {}: {}", topic, e.getMessage());
        }
    }
}

