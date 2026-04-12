package com.flashsale.paymentdomain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import com.flashsale.commonlib.event.KafkaTopics;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    @KafkaListener(topics = KafkaTopics.PAYMENT_SUCCESS, groupId = "payment-service-group")
    public void onPaymentSuccess(String transactionId) {
        log.info("Payment successful: {}", transactionId);
        // TODO: Implement
    }

    public void processPayment(String orderId, Long amount) {
        log.info("Processing payment for order: {}, amount: {}", orderId, amount);
        // TODO: Call Stripe API
    }
}

