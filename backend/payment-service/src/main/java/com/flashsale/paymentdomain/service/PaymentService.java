package com.flashsale.paymentdomain.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.paymentdomain.config.StripeConfig;
import com.flashsale.paymentdomain.domain.model.SellerStripeAccount;
import com.flashsale.paymentdomain.domain.model.SellerTransfer;
import com.flashsale.paymentdomain.domain.model.Transaction;
import com.flashsale.paymentdomain.domain.repository.SellerStripeAccountRepository;
import com.flashsale.paymentdomain.domain.repository.SellerTransferRepository;
import com.flashsale.paymentdomain.domain.repository.TransactionRepository;
import com.flashsale.paymentdomain.dto.response.SellerTransferInfo;
import com.flashsale.paymentdomain.dto.response.TransactionDetailResponse;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.*;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.math.RoundingMode;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final TransactionRepository transactionRepository;
    private final SellerTransferRepository sellerTransferRepository;
    private final SellerStripeAccountRepository sellerStripeAccountRepository;
    private final StripeConfig stripeConfig;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // ─── Query ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public TransactionDetailResponse getTransactionByParentOrder(Long parentOrderId) {
        Transaction tx = transactionRepository.findByParentOrderId(parentOrderId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "Không tìm thấy giao dịch cho parent order: " + parentOrderId));

        List<SellerTransfer> transfers = sellerTransferRepository.findAllByParentOrderId(parentOrderId);

        List<SellerTransferInfo> sellerInfos = transfers.stream()
                .map(t -> SellerTransferInfo.builder()
                        .sellerId(t.getSellerId())
                        .sellerName(t.getSellerName())
                        .orderId(t.getOrderId())
                        .amount(t.getTransferAmount())
                        .fee(t.getFeeAmount())
                        .netAmount(t.getNetAmount())
                        .stripeTransferId(t.getStripeTransferId())
                        .transferStatus(t.getStatus())
                        .build())
                .collect(Collectors.toList());

        Long remainingSeconds = null;
        if ("PENDING".equals(tx.getStatus()) && tx.getCreatedAt() != null) {
            long elapsed = java.time.Duration.between(tx.getCreatedAt(), LocalDateTime.now()).getSeconds();
            remainingSeconds = Math.max(0, 600 - elapsed);
        }

        return TransactionDetailResponse.builder()
                .transactionId(tx.getId())
                .parentOrderId(tx.getParentOrderId())
                .amount(tx.getAmount())
                .method(tx.getMethod())
                .status(tx.getStatus())
                .stripePiId(tx.getStripePiId())
                .applicationFee(tx.getApplicationFeeAmount())
                .applicationFeePercentage(tx.getApplicationFeePct())
                .transRef(tx.getTransRef())
                .paidAt(tx.getPayAt() != null ? tx.getPayAt().toInstant(ZoneOffset.UTC) : null)
                .remainingSeconds(remainingSeconds)
                .sellers(sellerInfos)
                .build();
    }

    // ─── Stripe Webhook ───────────────────────────────────────────────

    @Transactional
    public void handleStripeWebhook(String payload, String sigHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, stripeConfig.getWebhookSecret());
        } catch (SignatureVerificationException e) {
            log.warn("Invalid Stripe webhook signature: {}", e.getMessage());
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Invalid Stripe signature");
        }

        log.info("Processing Stripe webhook event: type={}, id={}", event.getType(), event.getId());

        switch (event.getType()) {
            case "payment_intent.succeeded"      -> handlePaymentIntentSucceeded(event);
            case "payment_intent.payment_failed" -> handlePaymentIntentFailed(event);
            case "charge.refunded"               -> handleChargeRefunded(event);
            case "account.updated"               -> handleAccountUpdated(event);
            case "transfer.created"              -> handleTransferCreated(event);
            default -> log.debug("Unhandled Stripe event type: {}", event.getType());
        }
    }

    private void handlePaymentIntentSucceeded(Event event) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(stripeObject instanceof PaymentIntent pi)) return;

        transactionRepository.findByParentOrderId(extractParentOrderId(pi.getMetadata()))
                .ifPresent(tx -> {
                    tx.setStatus("SUCCESS");
                    tx.setStripePiId(pi.getId());
                    tx.setPayAt(LocalDateTime.now());
                    transactionRepository.save(tx);

                    publish(KafkaTopics.PAYMENT_SUCCESS, String.valueOf(tx.getParentOrderId()), Map.of(
                            "parent_order_id", tx.getParentOrderId(),
                            "transaction_id",  tx.getId(),
                            "stripe_pi_id",    pi.getId(),
                            "amount",          tx.getAmount()
                    ));
                    log.info("Payment succeeded: parentOrderId={}, piId={}", tx.getParentOrderId(), pi.getId());
                });
    }

    private void handlePaymentIntentFailed(Event event) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(stripeObject instanceof PaymentIntent pi)) return;

        transactionRepository.findByParentOrderId(extractParentOrderId(pi.getMetadata()))
                .ifPresent(tx -> {
                    tx.setStatus("FAILED");
                    transactionRepository.save(tx);

                    publish(KafkaTopics.PAYMENT_FAILED, String.valueOf(tx.getParentOrderId()), Map.of(
                            "parent_order_id", tx.getParentOrderId(),
                            "transaction_id",  tx.getId(),
                            "stripe_pi_id",    pi.getId()
                    ));
                    log.info("Payment failed: parentOrderId={}", tx.getParentOrderId());
                });
    }

    private void handleChargeRefunded(Event event) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(stripeObject instanceof Charge charge)) return;

        publish(KafkaTopics.REFUND_STRIPE_AUTO, charge.getId(), Map.of(
                "charge_id",       charge.getId(),
                "amount_refunded", charge.getAmountRefunded(),
                "timestamp",       Instant.now().toString()
        ));
        log.info("Stripe charge refunded: chargeId={}, amount={}", charge.getId(), charge.getAmountRefunded());
    }

    private void handleAccountUpdated(Event event) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(stripeObject instanceof Account account)) return;

        sellerStripeAccountRepository.findByStripeAccountId(account.getId()).ifPresent(seller -> {
            seller.setDetailsSubmitted(Boolean.TRUE.equals(account.getDetailsSubmitted()));
            seller.setChargesEnabled(Boolean.TRUE.equals(account.getChargesEnabled()));
            seller.setPayoutsEnabled(Boolean.TRUE.equals(account.getPayoutsEnabled()));

            if ("restricted".equals(account.getRequirements().getDisabledReason())) {
                seller.setAccountStatus("SUSPENDED");
                publish(KafkaTopics.STRIPE_ACCOUNT_SUSPENDED, account.getId(), Map.of(
                        "seller_id",         seller.getSellerId(),
                        "stripe_account_id", account.getId()
                ));
            } else if (Boolean.TRUE.equals(account.getDetailsSubmitted())) {
                seller.setAccountStatus("ACTIVE");
                seller.setOnboardingUrl(null);
                seller.setOnboardingUrlExpiresAt(null);
            }
            sellerStripeAccountRepository.save(seller);
            log.info("Seller Stripe account synced: sellerId={}, status={}", seller.getSellerId(), seller.getAccountStatus());
        });
    }

    private void handleTransferCreated(Event event) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(stripeObject instanceof Transfer transfer)) return;

        Long orderId = extractOrderId(transfer.getMetadata());
        if (orderId == null) return;

        sellerTransferRepository.findByOrderId(orderId).ifPresent(st -> {
            st.setStripeTransferId(transfer.getId());
            st.setStatus("SUCCEEDED");
            sellerTransferRepository.save(st);
            log.info("Seller transfer recorded: orderId={}, transferId={}", orderId, transfer.getId());
        });
    }

    // ─── Kafka ────────────────────────────────────────────────────────

    @KafkaListener(topics = KafkaTopics.PAYMENT_REQUESTED, groupId = "payment-service-group")
    @Transactional
    public void onPaymentRequested(String message) {
        Long parentOrderId = null;
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            parentOrderId = toLong(payload.get("parent_order_id"));
            Long userId = toLong(payload.get("user_id"));
            java.math.BigDecimal totalAmount = toBigDecimal(payload.get("total_amount"));

            if (parentOrderId == null || totalAmount == null || totalAmount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                log.warn("Ignore payment.requested with invalid payload: {}", message);
                return;
            }

            Transaction existing = transactionRepository.findByParentOrderId(parentOrderId).orElse(null);
            if (existing != null && ("PENDING".equals(existing.getStatus()) || "SUCCESS".equals(existing.getStatus()))) {
                log.info("Skip payment.requested because transaction already exists: parentOrderId={}, status={}",
                        parentOrderId, existing.getStatus());
                return;
            }

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(toStripeAmount(totalAmount))
                    .setCurrency("vnd")
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build()
                    )
                    .putMetadata("parent_order_id", String.valueOf(parentOrderId))
                    .putMetadata("user_id", userId != null ? String.valueOf(userId) : "")
                    .build();

            PaymentIntent pi = PaymentIntent.create(params);

            Transaction tx = existing != null ? existing : new Transaction();
            tx.setParentOrderId(parentOrderId);
            tx.setAmount(totalAmount);
            tx.setMethod("STRIPE");
            tx.setStatus("PENDING");
            tx.setStripePiId(pi.getId());
            tx.setTransRef(buildTransRef(parentOrderId));
            tx.setRawResponse(pi.toJson());
            transactionRepository.save(tx);

            log.info("Payment initialized: parentOrderId={}, txId={}, piId={}", parentOrderId, tx.getId(), pi.getId());
        } catch (Exception e) {
            log.error("Failed to initialize payment from payment.requested: {}", e.getMessage(), e);
            if (parentOrderId != null) {
                publish(KafkaTopics.PAYMENT_FAILED, String.valueOf(parentOrderId), Map.of(
                        "parent_order_id", parentOrderId,
                        "reason", "Khoi tao thanh toan that bai",
                        "timestamp", Instant.now().toString()
                ));
            }
        }
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_SUCCESS, groupId = "payment-service-group")
    public void onPaymentSuccess(String message) {
        log.info("Payment success event received: {}", message);
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private long toStripeAmount(java.math.BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private String buildTransRef(Long parentOrderId) {
        return "TXN-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-" + parentOrderId;
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private java.math.BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        try {
            return new java.math.BigDecimal(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private void publish(String topic, String key, Object payload) {
        try {
            kafkaTemplate.send(topic, key, objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize Kafka payload for topic {}: {}", topic, e.getMessage());
        }
    }

    private Long extractParentOrderId(Map<String, String> metadata) {
        if (metadata == null) return null;
        try { return Long.parseLong(metadata.get("parent_order_id")); }
        catch (Exception e) { return null; }
    }

    private Long extractOrderId(Map<String, String> metadata) {
        if (metadata == null) return null;
        try { return Long.parseLong(metadata.get("order_id")); }
        catch (Exception e) { return null; }
    }
}
