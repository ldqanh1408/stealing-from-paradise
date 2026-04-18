package com.flashsale.orderdomain.config;

import com.flashsale.commonlib.event.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.HashMap;
import java.util.Map;

/**
 * Tạo tất cả Kafka topics cần thiết lúc startup qua KafkaAdmin.
 * Hoạt động ngay cả khi KAFKA_AUTO_CREATE_TOPICS_ENABLE=false trên broker.
 */
@Configuration
public class KafkaTopicConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        KafkaAdmin admin = new KafkaAdmin(configs);
        admin.setFatalIfBrokerNotAvailable(false);
        return admin;
    }

    // ─── Topics order-service consumes ────────────────────────────────────────
    @Bean public NewTopic paymentSuccess()   { return topic(KafkaTopics.PAYMENT_SUCCESS); }
    @Bean public NewTopic paymentFailed()    { return topic(KafkaTopics.PAYMENT_FAILED); }

    // ─── Reply topics (request-reply pattern) ─────────────────────────────────
    @Bean public NewTopic orderCartItemsResponse()    { return topic(KafkaTopics.ORDER_CART_ITEMS_RESPONSE); }
    @Bean public NewTopic orderAddressResponse()      { return topic(KafkaTopics.ORDER_ADDRESS_RESPONSE); }
    @Bean public NewTopic orderRefundsResponse()      { return topic(KafkaTopics.ORDER_REFUNDS_RESPONSE); }
    @Bean public NewTopic orderPaymentStatusResponse(){ return topic(KafkaTopics.ORDER_PAYMENT_STATUS_RESPONSE); }

    // ─── Topics order-service produces ────────────────────────────────────────
    @Bean public NewTopic orderCreated()           { return topic(KafkaTopics.ORDER_CREATED); }
    @Bean public NewTopic orderCheckoutCompleted() { return topic(KafkaTopics.ORDER_CHECKOUT_COMPLETED); }
    @Bean public NewTopic orderCancelled()         { return topic(KafkaTopics.ORDER_CANCELLED); }
    @Bean public NewTopic sellerOrderCancelled()   { return topic(KafkaTopics.SELLER_ORDER_CANCELLED); }
    @Bean public NewTopic orderShipped()           { return topic(KafkaTopics.ORDER_SHIPPED); }
    @Bean public NewTopic orderDelivered()         { return topic(KafkaTopics.ORDER_DELIVERED); }
    @Bean public NewTopic orderReturnedRts()       { return topic(KafkaTopics.ORDER_RETURNED_RTS); }
    @Bean public NewTopic refundRequested()        { return topic(KafkaTopics.REFUND_REQUESTED); }
    @Bean public NewTopic refundFullRequested()    { return topic(KafkaTopics.REFUND_FULL_REQUESTED); }

    // ─── Request topics (order-service sends, other services reply) ───────────
    @Bean public NewTopic orderCartItemsRequest()    { return topic(KafkaTopics.ORDER_CART_ITEMS_REQUEST); }
    @Bean public NewTopic orderAddressRequest()      { return topic(KafkaTopics.ORDER_ADDRESS_REQUEST); }
    @Bean public NewTopic orderRefundsRequest()      { return topic(KafkaTopics.ORDER_REFUNDS_REQUEST); }
    @Bean public NewTopic orderPaymentStatusRequest(){ return topic(KafkaTopics.ORDER_PAYMENT_STATUS_REQUEST); }
    @Bean public NewTopic orderStockCheckRequest()   { return topic(KafkaTopics.ORDER_STOCK_CHECK_REQUEST); }
    @Bean public NewTopic orderStockCheckResponse()  { return topic(KafkaTopics.ORDER_STOCK_CHECK_RESPONSE); }

    private NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }
}
