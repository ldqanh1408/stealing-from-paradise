package com.flashsale.orderdomain.service;

import com.flashsale.orderdomain.domain.model.Order;
import com.flashsale.orderdomain.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import com.flashsale.commonlib.event.KafkaTopics;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;

    @KafkaListener(topics = KafkaTopics.ORDER_CREATED, groupId = "order-service-group")
    public void onOrderCreated(String orderId) {
        log.info("Order created: {}", orderId);
        // TODO: Implement
    }

    public void deliverOrder(Long orderId) {
        log.info("Delivering order: {}", orderId);
        // TODO: Implement
    }

}

