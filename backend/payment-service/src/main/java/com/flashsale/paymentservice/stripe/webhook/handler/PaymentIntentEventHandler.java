package com.flashsale.paymentservice.stripe.webhook.handler;

import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.paymentservice.domain.model.Transaction;
import com.flashsale.paymentservice.domain.repository.TransactionRepository;
import com.flashsale.paymentservice.service.SellerTransferService;
import com.flashsale.paymentservice.stripe.webhook.StripeEventHandler;
import com.flashsale.paymentservice.support.KafkaPublisher;
import com.flashsale.paymentservice.support.StripeEvents;
import com.flashsale.paymentservice.support.StripeMetadata;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentIntentEventHandler implements StripeEventHandler {

    private final TransactionRepository transactionRepository;
    private final KafkaPublisher kafkaPublisher;
    private final SellerTransferService sellerTransferService;

    @Override
    @Transactional
    public void handle(Event event) {
        log.info("PaymentIntentEventHandler handling event type: {}", event.getType());
        switch (event.getType()) {
            case "payment_intent.succeeded" -> handlePaymentIntentSucceeded(event);
            case "payment_intent.payment_failed" -> handlePaymentIntentFailed(event);
            case "payment_intent.canceled" -> handlePaymentIntentCanceled(event);
            default -> log.warn("Unhandled PaymentIntent event type: {}", event.getType());
        }
    }

    private void handlePaymentIntentSucceeded(Event event) {
        StripeObject stripeObject = StripeEvents.deserialize(event);
        if (!(stripeObject instanceof PaymentIntent pi)) return;

        Long parentOrderId = StripeMetadata.extractParentOrderId(pi.getMetadata());
        if (parentOrderId == null) {
            log.warn("payment_intent.succeeded: missing parent_order_id in metadata, piId={}", pi.getId());
            return;
        }

        Long sellerId = com.flashsale.paymentservice.support.StripeAmounts.toLong(pi.getMetadata().get("seller_id"));
        if (sellerId == null) {
            log.warn("payment_intent.succeeded: missing seller_id in metadata, piId={}", pi.getId());
            return;
        }

        Transaction tx = transactionRepository.findByStripePaymentIntentId(pi.getId())
                .orElseGet(() -> transactionRepository.findAllByParentOrderId(parentOrderId).stream()
                        .filter(t -> sellerId.equals(t.getSellerId()))
                        .findFirst().orElse(null));

        if (tx == null) {
            log.warn("payment_intent.succeeded: no transaction found for piId={}, parentOrderId={}, sellerId={}", 
                     pi.getId(), parentOrderId, sellerId);
            return;
        }

        if (!"PENDING".equals(tx.getStatus())) {
            log.info("payment_intent.succeeded: transaction already processed, status={}, piId={}", tx.getStatus(), pi.getId());
            return;
        }

        tx.setStatus("SUCCESS");
        tx.setPayAt(LocalDateTime.now());
        if (pi.getLatestCharge() != null) {
            tx.setStripeChargeId(pi.getLatestCharge());
        }
        transactionRepository.save(tx);

        log.info("Payment succeeded for seller {} under parentOrderId={}: piId={}", sellerId, parentOrderId, pi.getId());

        // Update seller transfer record to AWAITING_DELIVERY for this specific seller
        sellerTransferService.createSellerTransfersForSeller(parentOrderId, sellerId);

        // Check if all transactions for this parent order have succeeded
        java.util.List<Transaction> allTxs = transactionRepository.findAllByParentOrderId(parentOrderId);
        boolean allSuccess = allTxs.stream().allMatch(t -> "SUCCESS".equals(t.getStatus()));
        if (allSuccess) {
            Long userId = StripeMetadata.extractUserId(pi.getMetadata());
            java.math.BigDecimal totalAmount = allTxs.stream()
                    .map(Transaction::getAmount)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("parent_order_id", parentOrderId);
            payload.put("transaction_id", tx.getId());
            payload.put("stripe_pi_id", pi.getId());
            payload.put("amount", totalAmount);
            if (userId != null) {
                payload.put("customer_id", userId);
            }

            kafkaPublisher.publish(KafkaTopics.PAYMENT_SUCCESS, String.valueOf(parentOrderId), payload);
            log.info("All payments succeeded for parentOrderId={}. Published PAYMENT_SUCCESS.", parentOrderId);
        }
    }

    private void handlePaymentIntentFailed(Event event) {
        StripeObject stripeObject = StripeEvents.deserialize(event);
        if (!(stripeObject instanceof PaymentIntent pi)) return;

        Long parentOrderId = StripeMetadata.extractParentOrderId(pi.getMetadata());
        if (parentOrderId == null) return;

        Transaction tx = transactionRepository.findByStripePaymentIntentId(pi.getId()).orElse(null);
        if (tx != null) {
            if ("PENDING".equals(tx.getStatus())) {
                tx.setStatus("FAILED");
                transactionRepository.save(tx);
            }
        }

        // Cancel/refund other payments of the parent order
        cancelOrRefundOtherPayments(parentOrderId, pi.getId());

        kafkaPublisher.publish(KafkaTopics.PAYMENT_FAILED, String.valueOf(parentOrderId), Map.of(
                "parent_order_id", parentOrderId,
                "reason",          "Payment failed on a sub-payment",
                "timestamp",       Instant.now().toString()
        ));
        log.info("Payment failed on sub-payment for parentOrderId={}, piId={}", parentOrderId, pi.getId());
    }

    private void handlePaymentIntentCanceled(Event event) {
        StripeObject stripeObject = StripeEvents.deserialize(event);
        if (!(stripeObject instanceof PaymentIntent pi)) return;

        Long parentOrderId = StripeMetadata.extractParentOrderId(pi.getMetadata());
        if (parentOrderId == null) return;

        Transaction tx = transactionRepository.findByStripePaymentIntentId(pi.getId()).orElse(null);
        if (tx != null) {
            if ("PENDING".equals(tx.getStatus())) {
                tx.setStatus("CANCELLED");
                transactionRepository.save(tx);
            }
        }

        // Cancel/refund other payments
        cancelOrRefundOtherPayments(parentOrderId, pi.getId());

        kafkaPublisher.publish(KafkaTopics.PAYMENT_FAILED, String.valueOf(parentOrderId), Map.of(
                "parent_order_id", parentOrderId,
                "reason",          "Payment canceled on a sub-payment",
                "timestamp",       Instant.now().toString()
        ));
        log.info("Payment canceled on sub-payment for parentOrderId={}, piId={}", parentOrderId, pi.getId());
    }

    private void cancelOrRefundOtherPayments(Long parentOrderId, String failedPiId) {
        java.util.List<Transaction> allTxs = transactionRepository.findAllByParentOrderId(parentOrderId);
        for (Transaction tx : allTxs) {
            if (failedPiId.equals(tx.getStripePaymentIntentId())) {
                continue;
            }
            if ("SUCCESS".equals(tx.getStatus()) && tx.getStripePaymentIntentId() != null) {
                try {
                    com.stripe.param.RefundCreateParams refundParams = com.stripe.param.RefundCreateParams.builder()
                            .setPaymentIntent(tx.getStripePaymentIntentId())
                            .setRefundApplicationFee(true)
                            .build();
                    com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                            .setStripeAccount(tx.getStripeAccountId())
                            .build();
                    com.stripe.model.Refund.create(refundParams, requestOptions);
                    tx.setStatus("REFUNDED");
                    transactionRepository.save(tx);
                    log.info("Refunded successful payment {} of seller {} due to partial checkout failure in parent order {}", tx.getStripePaymentIntentId(), tx.getSellerId(), parentOrderId);
                } catch (Exception e) {
                    log.error("Failed to refund successful payment {} of seller {} for parent order {}: {}", tx.getStripePaymentIntentId(), tx.getSellerId(), parentOrderId, e.getMessage());
                }
            } else if ("PENDING".equals(tx.getStatus()) && tx.getStripePaymentIntentId() != null) {
                try {
                    com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                            .setStripeAccount(tx.getStripeAccountId())
                            .build();
                    PaymentIntent pi = PaymentIntent.retrieve(tx.getStripePaymentIntentId(), requestOptions);
                    String piStatus = pi.getStatus();
                    if (!"canceled".equals(piStatus) && !"succeeded".equals(piStatus)) {
                        pi.cancel(requestOptions);
                        log.info("Cancelled pending payment intent {} of seller {} for parent order {}", tx.getStripePaymentIntentId(), tx.getSellerId(), parentOrderId);
                    }
                    tx.setStatus("CANCELLED");
                    transactionRepository.save(tx);
                } catch (Exception e) {
                    log.error("Failed to cancel pending payment intent {} of seller {} for parent order {}: {}", tx.getStripePaymentIntentId(), tx.getSellerId(), parentOrderId, e.getMessage());
                }
            }
        }
    }
}
