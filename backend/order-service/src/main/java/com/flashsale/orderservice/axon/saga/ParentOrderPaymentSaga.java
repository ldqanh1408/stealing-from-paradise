package com.flashsale.orderservice.axon.saga;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.orderservice.axon.event.*;
import com.flashsale.orderservice.domain.model.Order;
import com.flashsale.orderservice.domain.repository.OrderRepository;
import com.flashsale.orderservice.domain.repository.ParentOrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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

    @Autowired
    private transient ParentOrderRepository parentOrderRepository;

    @StartSaga
    @SagaEventHandler(associationProperty = "parentOrderId")
    public void on(ParentOrderCheckoutCreatedEvent event) {
        List<Order> subOrders = orderRepository.findAllByParentOrderIdAndStatus(event.getParentOrderId(), "PENDING");
        List<Map<String, Object>> orders = subOrders.stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("order_id",    o.getId());
            m.put("seller_id",   o.getSellerId());
            m.put("amount",      o.getFinalAmt());
            return m;
        }).toList();

        Map<String, Object> payload = new HashMap<>();
        payload.put("parent_order_id", event.getParentOrderId());
        payload.put("user_id", event.getUserId());
        payload.put("total_amount", event.getTotalAmount());
        payload.put("currency", "VND");
        payload.put("timestamp", Instant.now().toString());
        payload.put("orders", orders);

        send(KafkaTopics.PAYMENT_REQUESTED, String.valueOf(event.getParentOrderId()), payload);
        log.info("[ParentPaymentSaga][{}] Payment requested with {} sub-orders", event.getParentOrderId(), orders.size());
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "parentOrderId")
    @Transactional
    public void on(ParentOrderPaymentSucceededEvent event) {
        // Pessimistic lock prevents concurrent transactions from incrementing ParentOrder.version
        // while we process sub-orders. Previously this used findById (no lock), which caused
        // ObjectOptimisticLockingFailureException when another transaction committed first.
        parentOrderRepository.findByIdWithPessimisticLock(event.getParentOrderId());

        List<Order> orders = orderRepository.findAllByParentOrderIdAndStatusWithLock(event.getParentOrderId(), "PENDING");
        for (Order order : orders) {
            order.setStatus("PAID");
            orderRepository.save(order);
            eventGateway.publish(new OrderPaidEvent(
                    order.getId(),
                    order.getParentOrderId(),
                    order.getCustomerId(),
                    order.getSellerId(),
                    order.getFinalAmt()
            ));
        }

        log.info("[ParentPaymentSaga][{}] Payment succeeded, updated {} sub-orders", event.getParentOrderId(), orders.size());
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "parentOrderId")
    @Transactional
    public void on(ParentOrderPaymentFailedEvent event) {
        String failReason = event.getReason() == null || event.getReason().isBlank()
                ? "Thanh toan that bai"
                : event.getReason();

        // Pessimistic lock prevents concurrent transactions from incrementing ParentOrder.version
        // while we process sub-orders. Previously this used findById (no lock), which caused
        // ObjectOptimisticLockingFailureException when another transaction committed first.
        parentOrderRepository.findByIdWithPessimisticLock(event.getParentOrderId());

        List<Order> orders = orderRepository.findAllByParentOrderIdAndStatusWithLock(event.getParentOrderId(), "PENDING");
        for (Order order : orders) {
            order.setStatus("CANCELLED");
            order.setCancelledBy("SYSTEM");
            order.setCancelReason(failReason);
            orderRepository.save(order);

            eventGateway.publish(new OrderCancelledEvent(
                    order.getId(),
                    order.getParentOrderId(),
                    order.getCustomerId(),
                    order.getSellerId(),
                    "PAYMENT_FAILED",
                    failReason,
                    order.getTotalAmt()
            ));
        }

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

