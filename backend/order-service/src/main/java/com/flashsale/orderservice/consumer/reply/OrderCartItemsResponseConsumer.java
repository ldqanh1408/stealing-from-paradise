package com.flashsale.orderservice.consumer.reply;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.orderservice.service.KafkaReplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderCartItemsResponseConsumer {

    private final KafkaReplyService kafkaReplyService;

    @KafkaListener(topics = KafkaTopics.ORDER_CART_ITEMS_RESPONSE, groupId = "order-service-reply-group")
    public void onMessage(String message) {
        kafkaReplyService.onReply(message);
    }
}
