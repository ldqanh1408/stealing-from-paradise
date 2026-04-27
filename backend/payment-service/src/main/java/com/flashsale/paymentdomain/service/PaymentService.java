package com.flashsale.paymentdomain.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.event.payload.SellerStripeRequirementPayload;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.paymentdomain.config.StripeConfig;
import com.flashsale.paymentdomain.domain.model.SellerStripeAccount;
import com.flashsale.paymentdomain.domain.model.SellerTransfer;
import com.flashsale.paymentdomain.domain.model.Transaction;
import com.flashsale.paymentdomain.domain.repository.RefundRepository;
import com.flashsale.paymentdomain.domain.repository.SellerStripeAccountRepository;
import com.flashsale.paymentdomain.domain.repository.SellerTransferRepository;
import com.flashsale.paymentdomain.domain.repository.TransactionRepository;
import com.flashsale.paymentdomain.dto.response.ClientSecretResponse;
import com.flashsale.paymentdomain.dto.response.SellerTransferInfo;
import com.flashsale.paymentdomain.dto.response.TransactionDetailResponse;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.net.Webhook;
import com.stripe.param.AccountLinkCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.TransferCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final TransactionRepository transactionRepository;
    private final RefundRepository refundRepository;
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
        return buildTransactionDetailResponse(tx);
    }

    @Transactional(readOnly = true)
    public ClientSecretResponse getClientSecret(Long parentOrderId) {
        Transaction tx = transactionRepository.findByParentOrderId(parentOrderId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "Không tìm thấy giao dịch cho parent order: " + parentOrderId));

        if (tx.getClientSecret() == null) {
            throw new AppException(ErrorCode.NOT_FOUND,
                    "Client secret chưa được khởi tạo cho parent order: " + parentOrderId);
        }

        return ClientSecretResponse.builder()
                .parentOrderId(parentOrderId)
                .transactionId(tx.getId())
                .clientSecret(tx.getClientSecret())
                .status(tx.getStatus())
                .build();
    }

    /**
     * GET /api/v1/payments/by-intent/{stripePaymentIntentId}
     * Tra cứu giao dịch thanh toán qua Stripe PaymentIntent ID.
     * Dùng khi người dùng quay về từ redirect của Stripe mà không có context state.
     */
    @Transactional(readOnly = true)
    public TransactionDetailResponse getTransactionByStripePiId(String stripePiId) {
        Transaction tx = transactionRepository.findByStripePiId(stripePiId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "Không tìm thấy giao dịch với PaymentIntent: " + stripePiId));

        return buildTransactionDetailResponse(tx);
    }

    private TransactionDetailResponse buildTransactionDetailResponse(Transaction tx) {
        List<SellerTransfer> transfers = sellerTransferRepository.findAllByParentOrderId(tx.getParentOrderId());

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
            // ── PaymentIntent ──────────────────────────────────────────────
            case "payment_intent.succeeded"      -> handlePaymentIntentSucceeded(event);
            case "payment_intent.payment_failed" -> handlePaymentIntentFailed(event);
            case "payment_intent.canceled"       -> handlePaymentIntentCanceled(event);
            // ── Charge ─────────────────────────────────────────────────────
            case "charge.succeeded"              -> handleChargeSucceeded(event);
            case "charge.failed"                 -> handleChargeFailed(event);
            case "charge.refunded"               -> handleChargeRefunded(event);
            case "charge.refund.updated"         -> handleChargeRefundUpdated(event);
            case "charge.dispute.created"        -> handleDisputeCreated(event);
            case "charge.dispute.closed"         -> handleDisputeClosed(event);
            // ── Refund ─────────────────────────────────────────────────────
            case "refund.created"                -> handleRefundCreated(event);
            case "refund.updated"                -> handleRefundUpdated(event);
            // ── Transfer ───────────────────────────────────────────────────
            case "transfer.created"              -> handleTransferCreated(event);
            case "transfer.updated"              -> handleTransferUpdated(event);
            case "transfer.reversed"             -> handleTransferReversed(event);
            // ── Payout ─────────────────────────────────────────────────────
            case "payout.created"                -> handlePayoutCreated(event);
            case "payout.updated"                -> handlePayoutUpdated(event);
            case "payout.paid"                   -> handlePayoutPaid(event);
            case "payout.failed"                 -> handlePayoutFailed(event);
            // ── Account / External Accounts ────────────────────────────────
            case "account.updated"               -> handleAccountUpdated(event);
            case "account.external_account.created",
                 "account.external_account.updated",
                 "account.external_account.deleted" -> handleExternalAccountChanged(event);
            default -> log.debug("Unhandled Stripe event type: {}", event.getType());
        }
    }

    private void handlePaymentIntentSucceeded(Event event) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(stripeObject instanceof PaymentIntent pi)) return;

        Long parentOrderId = extractParentOrderId(pi.getMetadata());
        if (parentOrderId == null) {
            log.warn("payment_intent.succeeded: missing parent_order_id in metadata, piId={}", pi.getId());
            return;
        }

        transactionRepository.findByParentOrderId(parentOrderId).ifPresent(tx -> {
            // Calculate accumulated platform fee from all seller transfer records
            BigDecimal feeTotal = sellerTransferRepository.findAllByParentOrderId(parentOrderId)
                    .stream()
                    .map(SellerTransfer::getFeeAmount)
                    .filter(f -> f != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            tx.setStatus("SUCCESS");
            tx.setStripePiId(pi.getId());
            tx.setPayAt(LocalDateTime.now());
            tx.setApplicationFeeAmount(feeTotal);
            tx.setApplicationFeePct(BigDecimal.valueOf(stripeConfig.getPlatformFeePercentage()));
            transactionRepository.save(tx);

            publish(KafkaTopics.PAYMENT_SUCCESS, String.valueOf(tx.getParentOrderId()), Map.of(
                    "parent_order_id", tx.getParentOrderId(),
                    "transaction_id",  tx.getId(),
                    "stripe_pi_id",    pi.getId(),
                    "amount",          tx.getAmount()
            ));
            log.info("Payment succeeded: parentOrderId={}, piId={}", tx.getParentOrderId(), pi.getId());

            // Create Stripe Connect transfers to each seller
            createSellerTransfers(parentOrderId, pi);
        });
    }

    /**
     * Creates Stripe Connect transfers to each seller's connected account.
     * SellerTransfer records were created during onPaymentRequested().
     * Only transfers to sellers with chargesEnabled=true are executed.
     */
    private void createSellerTransfers(Long parentOrderId, PaymentIntent pi) {
        List<SellerTransfer> pendingTransfers = sellerTransferRepository.findAllByParentOrderId(parentOrderId)
                .stream()
                .filter(t -> "PENDING".equals(t.getStatus()))
                .toList();

        if (pendingTransfers.isEmpty()) {
            log.warn("No pending seller transfers for parentOrderId={}", parentOrderId);
            return;
        }

        String latestChargeId = pi.getLatestCharge();

        for (SellerTransfer st : pendingTransfers) {
            try {
                SellerStripeAccount sellerAccount = sellerStripeAccountRepository
                        .findBySellerId(st.getSellerId()).orElse(null);

                if (sellerAccount == null || !Boolean.TRUE.equals(sellerAccount.getChargesEnabled())) {
                    log.warn("Seller {} has no active Stripe account — skipping transfer for orderId={}",
                            st.getSellerId(), st.getOrderId());
                    st.setStatus("SKIPPED");
                    sellerTransferRepository.save(st);
                    continue;
                }

                TransferCreateParams params = TransferCreateParams.builder()
                        .setAmount(toStripeAmount(st.getNetAmount()))
                        .setCurrency("vnd")
                        .setDestination(sellerAccount.getStripeAccountId())
                        .putMetadata("order_id",    String.valueOf(st.getOrderId()))
                        .putMetadata("seller_id",   String.valueOf(st.getSellerId()))
                        .putMetadata("parent_order_id", String.valueOf(parentOrderId))
                        .setSourceTransaction(latestChargeId)
                        .build();

                Transfer transfer = Transfer.create(params);

                st.setStripeTransferId(transfer.getId());
                st.setStatus("SUCCEEDED");
                sellerTransferRepository.save(st);

                log.info("Stripe transfer created: orderId={}, sellerId={}, transferId={}, amount={}",
                        st.getOrderId(), st.getSellerId(), transfer.getId(), st.getNetAmount());

            } catch (StripeException e) {
                log.error("Stripe transfer failed for orderId={}, sellerId={}: {}",
                        st.getOrderId(), st.getSellerId(), e.getMessage());
                st.setStatus("FAILED");
                sellerTransferRepository.save(st);
            }
        }
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

            // Check requirements: if seller needs to complete additional Stripe requirements,
            // create a fresh Account Link and notify them via Kafka.
            var requirements = account.getRequirements();
            if (requirements != null && !requirements.getCurrentlyDue().isEmpty()) {
                try {
                    AccountLink accountLink = AccountLink.create(AccountLinkCreateParams.builder()
                            .setAccount(account.getId())
                            .setRefreshUrl(stripeConfig.getOnboardingRefreshUrl())
                            .setReturnUrl(stripeConfig.getOnboardingReturnUrl())
                            .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                            .build());

                    Instant expiresAt = Instant.now().plusSeconds(86400);
                    seller.setOnboardingUrl(accountLink.getUrl());
                    seller.setOnboardingUrlExpiresAt(LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC));

                    // Publish to notification service so seller receives the link
                    SellerStripeRequirementPayload notification = SellerStripeRequirementPayload.builder()
                            .sellerId(seller.getSellerId())
                            .stripeAccountId(account.getId())
                            .requirementType("verification_needed")
                            .requirementReason(String.join(", ", requirements.getCurrentlyDue()))
                            .accountLinkUrl(accountLink.getUrl())
                            .accountLinkExpiresAt(expiresAt.toEpochMilli())
                            .build();
                    publish(KafkaTopics.SELLER_STRIPE_REQUIREMENT, String.valueOf(seller.getSellerId()), notification);

                    log.info("Stripe requirements detected for seller {}: {}", seller.getSellerId(), requirements.getCurrentlyDue());
                } catch (StripeException e) {
                    log.error("Failed to create AccountLink for seller {} requirements: {}", seller.getSellerId(), e.getMessage());
                }
            }

            // Sync Express Dashboard URL for identity verification link
            seller.setExpressDashboardUrl("https://connect.stripe.com/express/" + account.getId());
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

    // ─── payment_intent.canceled ──────────────────────────────────────────────

    /**
     * Stripe hoặc hệ thống của chúng ta cancel PaymentIntent.
     * Cập nhật Transaction → CANCELLED và publish payment.failed để Saga kết thúc.
     */
    private void handlePaymentIntentCanceled(Event event) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(stripeObject instanceof PaymentIntent pi)) return;

        Long parentOrderId = extractParentOrderId(pi.getMetadata());
        if (parentOrderId == null) {
            log.warn("payment_intent.canceled: missing parent_order_id in metadata, piId={}", pi.getId());
            return;
        }

        transactionRepository.findByParentOrderId(parentOrderId).ifPresent(tx -> {
            if (!"PENDING".equals(tx.getStatus())) return;
            tx.setStatus("CANCELLED");
            transactionRepository.save(tx);
            publish(KafkaTopics.PAYMENT_FAILED, String.valueOf(parentOrderId), Map.of(
                    "parent_order_id", parentOrderId,
                    "transaction_id",  tx.getId(),
                    "stripe_pi_id",    pi.getId(),
                    "reason",          "PaymentIntent canceled",
                    "timestamp",       Instant.now().toString()
            ));
            log.info("PaymentIntent canceled → Transaction CANCELLED: parentOrderId={}, piId={}", parentOrderId, pi.getId());
        });
    }

    // ─── charge.succeeded ─────────────────────────────────────────────────────

    /**
     * Xác nhận charge thành công (fallback cho payment_intent.succeeded).
     * Thường payment_intent.succeeded đến trước; handler này đảm bảo idempotency.
     */
    private void handleChargeSucceeded(Event event) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(stripeObject instanceof Charge charge)) return;

        String piId = charge.getPaymentIntent();
        if (piId == null) return;

        transactionRepository.findByStripePiId(piId).ifPresent(tx -> {
            if ("SUCCESS".equals(tx.getStatus())) return; // đã xử lý bởi payment_intent.succeeded
            tx.setStatus("SUCCESS");
            tx.setPayAt(LocalDateTime.now());
            transactionRepository.save(tx);
            log.info("Charge succeeded (sync fallback): chargeId={}, txId={}", charge.getId(), tx.getId());
        });
    }

    // ─── charge.failed ────────────────────────────────────────────────────────

    /**
     * Charge thất bại ở tầng charge (không phải PaymentIntent).
     * Cập nhật Transaction → FAILED, publish payment.failed.
     */
    private void handleChargeFailed(Event event) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(stripeObject instanceof Charge charge)) return;

        String piId = charge.getPaymentIntent();
        if (piId == null) return;

        transactionRepository.findByStripePiId(piId).ifPresent(tx -> {
            if (!"PENDING".equals(tx.getStatus())) return;
            tx.setStatus("FAILED");
            transactionRepository.save(tx);
            publish(KafkaTopics.PAYMENT_FAILED, String.valueOf(tx.getParentOrderId()), Map.of(
                    "parent_order_id",  tx.getParentOrderId(),
                    "transaction_id",   tx.getId(),
                    "stripe_charge_id", charge.getId(),
                    "reason", charge.getFailureMessage() != null ? charge.getFailureMessage() : "Charge failed",
                    "timestamp",        Instant.now().toString()
            ));
            log.info("Charge failed: chargeId={}, txId={}, reason={}", charge.getId(), tx.getId(), charge.getFailureMessage());
        });
    }

    // ─── charge.dispute.created ───────────────────────────────────────────────

    /**
     * Buyer khiếu nại qua ngân hàng (chargeback). Publish alert để admin xử lý.
     * Stripe sẽ tự động giữ tiền trong dispute process.
     */
    private void handleDisputeCreated(Event event) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(stripeObject instanceof Dispute dispute)) return;

        publish(KafkaTopics.STRIPE_DISPUTE_CREATED, dispute.getId(), Map.of(
                "dispute_id",  dispute.getId(),
                "charge_id",   dispute.getCharge() != null ? dispute.getCharge() : "",
                "amount",      dispute.getAmount(),
                "currency",    dispute.getCurrency() != null ? dispute.getCurrency() : "",
                "reason",      dispute.getReason() != null ? dispute.getReason() : "",
                "status",      dispute.getStatus() != null ? dispute.getStatus() : "",
                "timestamp",   Instant.now().toString()
        ));
        log.warn("Stripe dispute CREATED: disputeId={}, chargeId={}, amount={}, reason={}",
                dispute.getId(), dispute.getCharge(), dispute.getAmount(), dispute.getReason());
    }

    // ─── charge.dispute.closed ────────────────────────────────────────────────

    /**
     * Dispute đã được phán xét (won / lost / warning_closed).
     * Nếu lost: Stripe đã trừ tiền từ platform → cần reconcile.
     */
    private void handleDisputeClosed(Event event) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(stripeObject instanceof Dispute dispute)) return;

        publish(KafkaTopics.STRIPE_DISPUTE_CLOSED, dispute.getId(), Map.of(
                "dispute_id", dispute.getId(),
                "charge_id",  dispute.getCharge() != null ? dispute.getCharge() : "",
                "outcome",    dispute.getStatus() != null ? dispute.getStatus() : "",
                "amount",     dispute.getAmount(),
                "timestamp",  Instant.now().toString()
        ));
        log.info("Stripe dispute CLOSED: disputeId={}, outcome={}, amount={}",
                dispute.getId(), dispute.getStatus(), dispute.getAmount());
    }

    // ─── refund.created ───────────────────────────────────────────────────────

    /**
     * Stripe xác nhận refund được tạo. Nếu refund_ref chưa có trong DB (refund
     * được khởi tạo bên ngoài hệ thống), chỉ log để admin biết.
     * Với refund do hệ thống tạo, DB đã được cập nhật trước khi Stripe gửi event này.
     */
    private void handleRefundCreated(Event event) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(stripeObject instanceof com.stripe.model.Refund stripeRefund)) return;

        boolean alreadyTracked = refundRepository.findByRefundRef(stripeRefund.getId()).isPresent();
        if (alreadyTracked) {
            log.debug("refund.created: already tracked in DB, stripeRefundId={}", stripeRefund.getId());
            return;
        }
        // Refund không có trong DB → có thể được tạo trực tiếp từ Stripe Dashboard
        log.warn("refund.created: untracked Stripe refund detected — stripeRefundId={}, piId={}, amount={}",
                stripeRefund.getId(), stripeRefund.getPaymentIntent(), stripeRefund.getAmount());
    }

    // ─── refund.updated / charge.refund.updated ───────────────────────────────

    /**
     * Trạng thái refund thay đổi phía Stripe (pending → succeeded/failed).
     * Cập nhật Refund.status trong DB để đồng bộ.
     */
    private void handleRefundUpdated(Event event) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(stripeObject instanceof com.stripe.model.Refund stripeRefund)) return;

        refundRepository.findByRefundRef(stripeRefund.getId()).ifPresent(refund -> {
            String newStatus = switch (stripeRefund.getStatus() != null ? stripeRefund.getStatus() : "") {
                case "succeeded"               -> "SUCCESS";
                case "failed", "canceled"      -> "FAILED";
                default                        -> refund.getStatus(); // pending: giữ nguyên
            };
            if (!newStatus.equals(refund.getStatus())) {
                refund.setStatus(newStatus);
                refundRepository.save(refund);
                log.info("Refund status synced from Stripe: refundId={}, stripeRefundId={}, status={}",
                        refund.getId(), stripeRefund.getId(), newStatus);
            }
        });
    }

    private void handleChargeRefundUpdated(Event event) {
        handleRefundUpdated(event); // same logic
    }

    // ─── transfer.updated ─────────────────────────────────────────────────────

    /**
     * Stripe cập nhật transfer (ví dụ: transfer_id đến muộn do out-of-order webhook).
     * Điền stripe_transfer_id nếu chưa có.
     */
    private void handleTransferUpdated(Event event) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(stripeObject instanceof Transfer transfer)) return;

        Long orderId = extractOrderId(transfer.getMetadata());
        if (orderId == null) return;

        sellerTransferRepository.findByOrderId(orderId).ifPresent(st -> {
            if (st.getStripeTransferId() == null) {
                st.setStripeTransferId(transfer.getId());
                sellerTransferRepository.save(st);
                log.info("SellerTransfer stripe_transfer_id backfilled: orderId={}, transferId={}", orderId, transfer.getId());
            }
        });
    }

    // ─── transfer.reversed ────────────────────────────────────────────────────

    /**
     * Transfer tới seller bị đảo ngược (thường do dispute thua kiện).
     * Cập nhật SellerTransfer → REVERSED và publish alert.
     */
    private void handleTransferReversed(Event event) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(stripeObject instanceof Transfer transfer)) return;

        Long orderId = extractOrderId(transfer.getMetadata());
        if (orderId == null) {
            log.warn("transfer.reversed without order_id metadata: transferId={}", transfer.getId());
            return;
        }

        sellerTransferRepository.findByOrderId(orderId).ifPresent(st -> {
            st.setStatus("REVERSED");
            sellerTransferRepository.save(st);
            publish(KafkaTopics.STRIPE_TRANSFER_REVERSED, String.valueOf(orderId), Map.of(
                    "order_id",       orderId,
                    "seller_id",      st.getSellerId(),
                    "transfer_id",    transfer.getId(),
                    "amount_reversed", transfer.getAmountReversed(),
                    "timestamp",      Instant.now().toString()
            ));
            log.warn("Transfer REVERSED: orderId={}, sellerId={}, transferId={}, amountReversed={}",
                    orderId, st.getSellerId(), transfer.getId(), transfer.getAmountReversed());
        });
    }

    // ─── payout events ────────────────────────────────────────────────────────

    /** Seller tạo lệnh rút tiền về ngân hàng. */
    private void handlePayoutCreated(Event event) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(stripeObject instanceof Payout payout)) return;
        log.info("Payout CREATED: payoutId={}, amount={}, arrivalDate={}",
                payout.getId(), payout.getAmount(), payout.getArrivalDate());
    }

    /** Seller cập nhật thông tin lệnh rút tiền. */
    private void handlePayoutUpdated(Event event) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(stripeObject instanceof Payout payout)) return;
        log.info("Payout UPDATED: payoutId={}, status={}", payout.getId(), payout.getStatus());
    }

    /** Tiền đã về tài khoản ngân hàng của Seller thành công. */
    private void handlePayoutPaid(Event event) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(stripeObject instanceof Payout payout)) return;
        log.info("Payout PAID: payoutId={}, amount={}", payout.getId(), payout.getAmount());
    }

    /** Rút tiền thất bại — publish alert để notify Seller và admin. */
    private void handlePayoutFailed(Event event) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (!(stripeObject instanceof Payout payout)) return;

        publish(KafkaTopics.STRIPE_PAYOUT_FAILED, payout.getId(), Map.of(
                "payout_id",       payout.getId(),
                "amount",          payout.getAmount(),
                "failure_code",    payout.getFailureCode()    != null ? payout.getFailureCode()    : "",
                "failure_message", payout.getFailureMessage() != null ? payout.getFailureMessage() : "",
                "timestamp",       Instant.now().toString()
        ));
        log.warn("Payout FAILED: payoutId={}, amount={}, failureCode={}, failureMsg={}",
                payout.getId(), payout.getAmount(), payout.getFailureCode(), payout.getFailureMessage());
    }

    // ─── account.external_account.* ──────────────────────────────────────────

    /**
     * Seller thêm / sửa / xóa tài khoản ngân hàng trên Stripe Connect.
     * account.updated sẽ cập nhật trạng thái payouts_enabled; handler này chỉ log.
     */
    private void handleExternalAccountChanged(Event event) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (stripeObject == null) return;

        String accountId = null;
        String bankAccountId = null;

        if (stripeObject instanceof BankAccount bankAccount) {
            accountId    = bankAccount.getAccount();
            bankAccountId = bankAccount.getId();
        } else if (stripeObject instanceof Card card) {
            accountId    = card.getAccount();
            bankAccountId = card.getId();
        }

        if (accountId == null) return;

        final String finalAccountId    = accountId;
        final String finalBankAccountId = bankAccountId;
        sellerStripeAccountRepository.findByStripeAccountId(finalAccountId).ifPresent(seller ->
            log.info("External bank account changed [{}]: sellerId={}, stripeAccountId={}, bankAccountId={}",
                    event.getType(), seller.getSellerId(), finalAccountId, finalBankAccountId)
        );
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
            BigDecimal totalAmount = toBigDecimal(payload.get("total_amount"));

            if (parentOrderId == null || totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Ignore payment.requested with invalid payload: {}", message);
                return;
            }

            // Idempotency: skip if transaction already exists and is usable
            Transaction existing = transactionRepository.findByParentOrderId(parentOrderId).orElse(null);
            if (existing != null && ("PENDING".equals(existing.getStatus()) || "SUCCESS".equals(existing.getStatus()))) {
                log.info("Skip payment.requested — transaction already exists: parentOrderId={}, status={}",
                        parentOrderId, existing.getStatus());
                return;
            }

            // Create Stripe PaymentIntent
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(toStripeAmount(totalAmount))
                    .setCurrency("vnd")
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                                    .build()
                    )
                    .putMetadata("parent_order_id", String.valueOf(parentOrderId))
                    .putMetadata("user_id", userId != null ? String.valueOf(userId) : "")
                    .build();

            PaymentIntent pi = PaymentIntent.create(params);

            // Persist transaction with client_secret for frontend
            Transaction tx = existing != null ? existing : new Transaction();
            tx.setParentOrderId(parentOrderId);
            tx.setAmount(totalAmount);
            tx.setMethod("STRIPE");
            tx.setStatus("PENDING");
            tx.setStripePiId(pi.getId());
            tx.setClientSecret(pi.getClientSecret());
            tx.setTransRef(buildTransRef(parentOrderId));
            tx.setRawResponse(pi.toJson());
            transactionRepository.save(tx);

            log.info("Payment initialized: parentOrderId={}, txId={}, piId={}", parentOrderId, tx.getId(), pi.getId());

            // Create SellerTransfer records from sub-order breakdown in payload
            createSellerTransferRecords(parentOrderId, payload, tx.getId());

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

    /**
     * Parses the sub-order list from payment.requested payload and creates
     * PENDING SellerTransfer records so that when payment succeeds,
     * Stripe transfers can be executed immediately.
     */
    @SuppressWarnings("unchecked")
    private void createSellerTransferRecords(Long parentOrderId, Map<String, Object> payload, Long transactionId) {
        List<?> orders = (List<?>) payload.get("orders");
        if (orders == null || orders.isEmpty()) {
            log.warn("payment.requested for parentOrderId={} has no orders array — seller transfers skipped", parentOrderId);
            return;
        }

        double feePct = stripeConfig.getPlatformFeePercentage();

        for (Object raw : orders) {
            Map<String, Object> order = (Map<String, Object>) raw;
            Long orderId   = toLong(order.get("order_id"));
            Long sellerId  = toLong(order.get("seller_id"));
            String sellerName = (String) order.get("seller_name");
            BigDecimal amount = toBigDecimal(order.get("amount"));

            if (orderId == null || sellerId == null || amount == null) continue;

            // Idempotency: skip if transfer record already exists for this order
            if (sellerTransferRepository.findByOrderId(orderId).isPresent()) {
                log.info("SellerTransfer already exists for orderId={}, skipping", orderId);
                continue;
            }

            BigDecimal fee       = amount.multiply(BigDecimal.valueOf(feePct / 100.0)).setScale(0, RoundingMode.HALF_UP);
            BigDecimal netAmount = amount.subtract(fee);

            SellerTransfer st = SellerTransfer.builder()
                    .parentOrderId(parentOrderId)
                    .orderId(orderId)
                    .sellerId(sellerId)
                    .sellerName(sellerName)
                    .transferAmount(amount)
                    .feeAmount(fee)
                    .netAmount(netAmount)
                    .status("PENDING")
                    .build();
            sellerTransferRepository.save(st);

            log.info("SellerTransfer record created: orderId={}, sellerId={}, amount={}, fee={}, net={}",
                    orderId, sellerId, amount, fee, netAmount);
        }
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_SUCCESS, groupId = "payment-service-group")
    public void onPaymentSuccess(String message) {
        log.info("Payment success event received: {}", message);
    }

    /**
     * Nhận ORDER_CANCELLED và ORDER_AUTO_CANCELLED từ order-service.
     * Nếu Transaction vẫn ở PENDING, hủy Stripe PaymentIntent để tránh
     * buyer vô tình thanh toán cho đơn đã bị hủy.
     * Sau đó publish payment.failed để ParentOrderPaymentSaga kết thúc.
     */
    @KafkaListener(
        topics = {KafkaTopics.ORDER_CANCELLED, KafkaTopics.ORDER_AUTO_CANCELLED},
        groupId = "payment-service-group"
    )
    @Transactional
    public void onOrderCancelled(String message) {
        Long parentOrderId = null;
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            parentOrderId = toLong(payload.get("parent_order_id"));
            if (parentOrderId == null) {
                log.warn("onOrderCancelled: missing parent_order_id in payload");
                return;
            }

            final Long finalParentOrderId = parentOrderId;
            Transaction tx = transactionRepository.findByParentOrderId(parentOrderId).orElse(null);

            if (tx == null) {
                // Đơn bị hủy trước khi payment-service xử lý payment.requested —
                // vẫn publish payment.failed để Saga có thể kết thúc sạch sẽ.
                log.warn("onOrderCancelled: no transaction for parentOrderId={} — publishing payment.failed for saga cleanup", parentOrderId);
                publish(KafkaTopics.PAYMENT_FAILED, String.valueOf(finalParentOrderId), Map.of(
                        "parent_order_id", finalParentOrderId,
                        "reason",          "Order cancelled before payment",
                        "timestamp",       Instant.now().toString()
                ));
                return;
            }

            if (!"PENDING".equals(tx.getStatus())) {
                log.debug("onOrderCancelled: skip — transaction not PENDING: parentOrderId={}, status={}",
                        parentOrderId, tx.getStatus());
                return;
            }

            // Hủy Stripe PaymentIntent nếu vẫn còn khả dụng
            if (tx.getStripePiId() != null) {
                try {
                    PaymentIntent pi = PaymentIntent.retrieve(tx.getStripePiId());
                    String piStatus = pi.getStatus();
                    if (!"canceled".equals(piStatus) && !"succeeded".equals(piStatus)) {
                        pi.cancel();
                        log.info("Stripe PI cancelled: parentOrderId={}, piId={}", parentOrderId, tx.getStripePiId());
                    }
                } catch (StripeException e) {
                    // PI không hủy được (đã expired hoặc lỗi Stripe) — vẫn tiếp tục cập nhật DB
                    log.error("Could not cancel Stripe PI {}: {}", tx.getStripePiId(), e.getMessage());
                }
            }

            tx.setStatus("CANCELLED");
            transactionRepository.save(tx);

            publish(KafkaTopics.PAYMENT_FAILED, String.valueOf(parentOrderId), Map.of(
                    "parent_order_id", parentOrderId,
                    "transaction_id",  tx.getId(),
                    "reason",          "Order cancelled",
                    "timestamp",       Instant.now().toString()
            ));
            log.info("Transaction cancelled after order cancellation: parentOrderId={}, txId={}", parentOrderId, tx.getId());

        } catch (Exception e) {
            log.error("Error processing order.cancelled for parentOrderId={}: {}", parentOrderId, e.getMessage(), e);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private long toStripeAmount(BigDecimal amount) {
        // VND is a zero-decimal currency in Stripe — amount is already in the smallest unit
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

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        try {
            return new BigDecimal(value.toString());
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
