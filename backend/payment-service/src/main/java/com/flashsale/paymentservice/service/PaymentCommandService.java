package com.flashsale.paymentservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.paymentservice.domain.model.Transaction;
import com.flashsale.paymentservice.domain.repository.TransactionRepository;
import com.flashsale.paymentservice.support.KafkaPublisher;
import com.flashsale.paymentservice.support.StripeAmounts;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import com.flashsale.paymentservice.domain.repository.SellerStripeAccountRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCommandService {

    private final TransactionRepository transactionRepository;
    private final SellerTransferService sellerTransferService;
    private final KafkaPublisher kafkaPublisher;
    private final ObjectMapper objectMapper;
    private final SellerStripeAccountRepository sellerStripeAccountRepository;
    private final com.flashsale.paymentservice.config.StripeConfig stripeConfig;

    @Transactional
    public void onPaymentRequested(String message) {
        Long parentOrderId = null;
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            parentOrderId = StripeAmounts.toLong(payload.get("parent_order_id"));
            Long userId = StripeAmounts.toLong(payload.get("user_id"));
            BigDecimal totalAmount = StripeAmounts.toBigDecimal(payload.get("total_amount"));

            if (parentOrderId == null || totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Ignore payment.requested with invalid payload: {}", message);
                return;
            }

            // Idempotency: skip if transactions already exist
            java.util.List<Transaction> existingList = transactionRepository.findAllByParentOrderId(parentOrderId);
            if (!existingList.isEmpty()) {
                log.info("Skip payment.requested — transaction already exists: parentOrderId={}", parentOrderId);
                return;
            }

            List<?> orders = (List<?>) payload.get("orders");
            if (orders == null || orders.isEmpty()) {
                log.warn("payment.requested for parentOrderId={} has no orders array", parentOrderId);
                return;
            }

            // Map sellerId -> list of orders
            Map<Long, java.util.List<Map<String, Object>>> ordersBySeller = new java.util.HashMap<>();
            for (Object raw : orders) {
                Map<String, Object> order = (Map<String, Object>) raw;
                Long sellerId = StripeAmounts.toLong(order.get("seller_id"));
                if (sellerId != null) {
                    ordersBySeller.computeIfAbsent(sellerId, k -> new java.util.ArrayList<>()).add(order);
                }
            }

            double feePct = stripeConfig.getPlatformFeePercentage();

            for (Map.Entry<Long, java.util.List<Map<String, Object>>> entry : ordersBySeller.entrySet()) {
                Long sellerId = entry.getKey();
                java.util.List<Map<String, Object>> sellerOrders = entry.getValue();

                BigDecimal sellerTotalAmount = BigDecimal.ZERO;
                for (Map<String, Object> o : sellerOrders) {
                    BigDecimal amount = StripeAmounts.toBigDecimal(o.get("amount"));
                    if (amount != null) {
                        sellerTotalAmount = sellerTotalAmount.add(amount);
                    }
                }

                BigDecimal commission = sellerTotalAmount.multiply(BigDecimal.valueOf(feePct / 100.0)).setScale(0, java.math.RoundingMode.HALF_UP);

                com.flashsale.paymentservice.domain.model.SellerStripeAccount sellerAccount = sellerStripeAccountRepository
                        .findBySellerId(sellerId)
                        .orElseThrow(() -> new RuntimeException("Seller " + sellerId + " has no Stripe account linked"));

                if (!Boolean.TRUE.equals(sellerAccount.getChargesEnabled())) {
                    throw new RuntimeException("Seller " + sellerId + " has Stripe account charges disabled");
                }

                String stripeAccountId = sellerAccount.getStripeAccountId();

                PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                        .setAmount(StripeAmounts.toStripeAmount(sellerTotalAmount))
                        .setCurrency("vnd")
                        .setApplicationFeeAmount(StripeAmounts.toStripeAmount(commission))
                        .setAutomaticPaymentMethods(
                                PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                        .setEnabled(true)
                                        .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                                        .build()
                        )
                        .putMetadata("parent_order_id", String.valueOf(parentOrderId))
                        .putMetadata("seller_id", String.valueOf(sellerId))
                        .putMetadata("user_id", userId != null ? String.valueOf(userId) : "")
                        .putMetadata("order_ids", sellerOrders.stream().map(o -> String.valueOf(o.get("order_id"))).collect(java.util.stream.Collectors.joining(",")))
                        .build();

                com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                        .setStripeAccount(stripeAccountId)
                        .build();

                PaymentIntent pi = PaymentIntent.create(params, requestOptions);

                Transaction tx = new Transaction();
                tx.setParentOrderId(parentOrderId);
                tx.setOrderId(StripeAmounts.toLong(sellerOrders.get(0).get("order_id")));
                tx.setSellerId(sellerId);
                tx.setStripeAccountId(stripeAccountId);
                tx.setStripePaymentIntentId(pi.getId());
                tx.setAmount(sellerTotalAmount);
                tx.setGrossAmount(sellerTotalAmount);
                tx.setApplicationFeeAmount(commission);
                tx.setSellerNetAmount(sellerTotalAmount.subtract(commission));
                tx.setCurrency("vnd");
                tx.setStatus("PENDING");
                tx.setStripeConnectMode("DIRECT_CHARGE");
                tx.setTransRef(StripeAmounts.buildTransRef(parentOrderId) + "-" + sellerId);
                tx.setRawResponse(pi.toJson());
                transactionRepository.save(tx);

                log.info("Payment initialized for seller: parentOrderId={}, sellerId={}, txId={}, piId={}", 
                         parentOrderId, sellerId, tx.getId(), pi.getId());
            }

            sellerTransferService.createSellerTransferRecords(parentOrderId, payload, 0L);

        } catch (Exception e) {
            log.error("Failed to initialize payment from payment.requested: {}", e.getMessage(), e);
            if (parentOrderId != null) {
                kafkaPublisher.publish(KafkaTopics.PAYMENT_FAILED, String.valueOf(parentOrderId), Map.of(
                        "parent_order_id", parentOrderId,
                        "reason", "Khoi tao thanh toan that bai: " + e.getMessage(),
                        "timestamp", Instant.now().toString()
                ));
            }
        }
    }

    /**
     * Nhận ORDER_CANCELLED và ORDER_AUTO_CANCELLED từ order-service.
     * Nếu Transaction vẫn ở PENDING, hủy Stripe PaymentIntent để tránh
     * buyer vô tình thanh toán cho đơn đã bị hủy.
     * Sau đó publish payment.failed để ParentOrderPaymentSaga kết thúc.
     */
    @Transactional
    public void onOrderCancelled(String message) {
        Long parentOrderId = null;
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            parentOrderId = StripeAmounts.toLong(payload.get("parent_order_id"));
            if (parentOrderId == null) {
                log.warn("onOrderCancelled: missing parent_order_id in payload");
                return;
            }

            final Long finalParentOrderId = parentOrderId;
            List<Transaction> txs = transactionRepository.findAllByParentOrderId(parentOrderId);

            if (txs.isEmpty()) {
                log.warn("onOrderCancelled: no transaction for parentOrderId={} — publishing payment.failed for saga cleanup", parentOrderId);
                kafkaPublisher.publish(KafkaTopics.PAYMENT_FAILED, String.valueOf(finalParentOrderId), Map.of(
                        "parent_order_id", finalParentOrderId,
                        "reason",          "Order cancelled before payment",
                        "timestamp",       Instant.now().toString()
                ));
                return;
            }

            for (Transaction tx : txs) {
                if (!"PENDING".equals(tx.getStatus())) {
                    log.debug("onOrderCancelled: skip — transaction not PENDING: parentOrderId={}, status={}",
                            parentOrderId, tx.getStatus());
                    continue;
                }

                String stripePiId = tx.getStripePaymentIntentId();
                if (stripePiId != null) {
                    try {
                        com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                                .setStripeAccount(tx.getStripeAccountId())
                                .build();
                        PaymentIntent pi = PaymentIntent.retrieve(stripePiId, requestOptions);
                        String piStatus = pi.getStatus();
                        if (!"canceled".equals(piStatus) && !"succeeded".equals(piStatus)) {
                            pi.cancel(requestOptions);
                            log.info("Stripe PI cancelled: parentOrderId={}, piId={}", parentOrderId, stripePiId);
                        }
                    } catch (StripeException e) {
                        log.error("Could not cancel Stripe PI {}: {}", stripePiId, e.getMessage());
                    }
                }

                tx.setStatus("CANCELLED");
                transactionRepository.save(tx);
            }

            kafkaPublisher.publish(KafkaTopics.PAYMENT_FAILED, String.valueOf(parentOrderId), Map.of(
                    "parent_order_id", parentOrderId,
                    "reason",          "Order cancelled",
                    "timestamp",       Instant.now().toString()
            ));
            log.info("Transactions cancelled after order cancellation for parentOrderId={}", parentOrderId);

        } catch (Exception e) {
            log.error("Error processing order.cancelled for parentOrderId={}: {}", parentOrderId, e.getMessage(), e);
        }
    }

    /**
     * Extract the Stripe PaymentIntent ID from the rawResponse JSON stored on a Transaction.
     * Returns null if rawResponse is null or cannot be parsed.
     */
    private String extractPiIdFromRawResponse(String rawResponse) {
        if (rawResponse == null) return null;
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(rawResponse);
            return node.has("id") ? node.get("id").asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
