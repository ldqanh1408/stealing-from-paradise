package com.flashsale.paymentservice.config;

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

    // ─── Topics payment-service consumes ──────────────────────────────────────
    @Bean public NewTopic paymentRequested()         { return topic(KafkaTopics.PAYMENT_REQUESTED); }
    @Bean public NewTopic paymentSuccess()           { return topic(KafkaTopics.PAYMENT_SUCCESS); }
    @Bean public NewTopic orderPaymentStatusReq()    { return topic(KafkaTopics.ORDER_PAYMENT_STATUS_REQUEST); }
    @Bean public NewTopic refundRequested()          { return topic(KafkaTopics.REFUND_REQUESTED); }
    @Bean public NewTopic refundFullRequested()      { return topic(KafkaTopics.REFUND_FULL_REQUESTED); }
    @Bean public NewTopic orderReturnedRts()         { return topic(KafkaTopics.ORDER_RETURNED_RTS); }
    @Bean public NewTopic orderRefundsRequest()      { return topic(KafkaTopics.ORDER_REFUNDS_REQUEST); }
    @Bean public NewTopic orderCancelled()           { return topic(KafkaTopics.ORDER_CANCELLED); }
    @Bean public NewTopic orderAutoCancelled()       { return topic(KafkaTopics.ORDER_AUTO_CANCELLED); }

    // ─── Topics payment-service produces ──────────────────────────────────────
    @Bean public NewTopic paymentFailed()              { return topic(KafkaTopics.PAYMENT_FAILED); }
    @Bean public NewTopic refundStripeAuto()           { return topic(KafkaTopics.REFUND_STRIPE_AUTO); }
    @Bean public NewTopic refundCreated()              { return topic(KafkaTopics.REFUND_CREATED); }
    @Bean public NewTopic stripeAccountSuspended()     { return topic(KafkaTopics.STRIPE_ACCOUNT_SUSPENDED); }
    @Bean public NewTopic orderPaymentStatusResponse() { return topic(KafkaTopics.ORDER_PAYMENT_STATUS_RESPONSE); }
    @Bean public NewTopic orderRefundsResponse()       { return topic(KafkaTopics.ORDER_REFUNDS_RESPONSE); }
    @Bean public NewTopic refundAdminApproved()        { return topic(KafkaTopics.REFUND_ADMIN_APPROVED); }
    @Bean public NewTopic refundRejected()             { return topic(KafkaTopics.REFUND_REJECTED); }
    @Bean public NewTopic refundRtsCompleted()         { return topic(KafkaTopics.REFUND_RTS_COMPLETED); }
    @Bean public NewTopic stripeDisputeCreated()       { return topic(KafkaTopics.STRIPE_DISPUTE_CREATED); }
    @Bean public NewTopic stripeDisputeClosed()        { return topic(KafkaTopics.STRIPE_DISPUTE_CLOSED); }
    @Bean public NewTopic stripeTransferReversed()     { return topic(KafkaTopics.STRIPE_TRANSFER_REVERSED); }
    @Bean public NewTopic stripePayoutFailed()         { return topic(KafkaTopics.STRIPE_PAYOUT_FAILED); }

    private NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }
}
