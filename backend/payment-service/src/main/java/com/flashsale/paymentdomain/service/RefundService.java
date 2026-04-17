package com.flashsale.paymentdomain.service;

import com.flashsale.commonlib.dto.PageResponse;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.paymentdomain.domain.model.Refund;
import com.flashsale.paymentdomain.domain.model.RefundItem;
import com.flashsale.paymentdomain.domain.model.Transaction;
import com.flashsale.paymentdomain.domain.repository.RefundItemRepository;
import com.flashsale.paymentdomain.domain.repository.RefundRepository;
import com.flashsale.paymentdomain.domain.repository.TransactionRepository;
import com.flashsale.paymentdomain.dto.request.AdminRefundApproveRequest;
import com.flashsale.paymentdomain.dto.request.AdminRefundRejectRequest;
import com.flashsale.paymentdomain.dto.response.AdminRefundApproveResponse;
import com.flashsale.paymentdomain.dto.response.RefundListResponse;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.RefundCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundService {

    private final RefundRepository refundRepository;
    private final RefundItemRepository refundItemRepository;
    private final TransactionRepository transactionRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // ─── Admin: List Refunds ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<RefundListResponse> listAllRefunds(
            String status, String type,
            String fromDate, String toDate,
            int page, int size) {

        LocalDateTime from = fromDate != null
                ? LocalDateTime.parse(fromDate + "T00:00:00")
                : null;
        LocalDateTime to = toDate != null
                ? LocalDateTime.parse(toDate + "T23:59:59")
                : null;

        Page<Refund> result = refundRepository.findAllWithFilters(
                status, type, from, to,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<RefundListResponse> content = result.getContent().stream()
                .map(this::toListResponse)
                .collect(Collectors.toList());

        return PageResponse.<RefundListResponse>builder()
                .content(content)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build();
    }

    // ─── Admin: Approve Refund ────────────────────────────────────────

    @Transactional
    public AdminRefundApproveResponse approveRefund(Long refundId, Long adminId, AdminRefundApproveRequest req) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Refund không tồn tại: " + refundId));

        if (!"PENDING".equals(refund.getStatus()) && !"FAILED".equals(refund.getStatus())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Chỉ có thể approve refund ở trạng thái PENDING hoặc FAILED");
        }

        BigDecimal finalAmount = req.getAdjustAmount() != null ? req.getAdjustAmount() : refund.getAmount();

        String stripeRefundId = executeStripeRefund(refund.getTransactionId(), finalAmount);

        // Update tracking numbers on refund items if provided
        List<AdminRefundApproveResponse.ReturnEvidence> returnEvidence = new ArrayList<>();
        if (req.getTrackingNumber() != null) {
            List<RefundItem> items = refundItemRepository.findAllByRefundId(refundId);
            Instant now = Instant.now();
            for (RefundItem item : items) {
                item.setReturnTrackingNumber(req.getTrackingNumber());
                item.setReturnedAt(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
            }
            refundItemRepository.saveAll(items);

            returnEvidence.add(AdminRefundApproveResponse.ReturnEvidence.builder()
                    .type("tracking")
                    .trackingNumber(req.getTrackingNumber())
                    .recordedAt(now)
                    .build());
        }

        refund.setStatus("SUCCESS");
        refund.setAdminNote(req.getAdminNote());
        refund.setAdjustAmount(req.getAdjustAmount());
        refund.setReviewedBy(adminId);
        refund.setReviewedAt(LocalDateTime.now());
        refund.setRefundRef(stripeRefundId);
        refundRepository.save(refund);

        // Publish Kafka event
        kafkaTemplate.send(KafkaTopics.REFUND_ADMIN_APPROVED, String.valueOf(refundId),
                Map.of(
                    "refund_id", refundId,
                    "order_id", refund.getOrderId(),
                    "amount", finalAmount,
                    "admin_id", adminId,
                    "caused_by", req.getCausedBy() != null ? req.getCausedBy() : "",
                    "tracking_number", req.getTrackingNumber() != null ? req.getTrackingNumber() : "",
                    "timestamp", Instant.now().toString()
                ));

        log.info("Refund approved: refundId={}, adminId={}, amount={}, stripeRefundId={}",
                refundId, adminId, finalAmount, stripeRefundId);

        return AdminRefundApproveResponse.builder()
                .refundId(refund.getId())
                .refundCode(buildRefundCode(refund))
                .status("SUCCESS")
                .type(refund.getType())
                .amount(finalAmount)
                .adjustAmount(req.getAdjustAmount())
                .trackingNumber(req.getTrackingNumber())
                .returnEvidence(returnEvidence.isEmpty() ? null : returnEvidence)
                .reviewedBy(adminId)
                .adminNote(req.getAdminNote())
                .reviewedAt(refund.getReviewedAt().toInstant(ZoneOffset.UTC))
                .stripeRefundId(stripeRefundId)
                .build();
    }

    // ─── Admin: Reject Refund ─────────────────────────────────────────

    @Transactional
    public void rejectRefund(Long refundId, Long adminId, AdminRefundRejectRequest req) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Refund không tồn tại: " + refundId));

        if (!"PENDING".equals(refund.getStatus())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Chỉ có thể reject refund ở trạng thái PENDING");
        }

        refund.setStatus("REJECTED");
        refund.setRejectReason(req.getRejectReason());
        refund.setReviewedBy(adminId);
        refund.setReviewedAt(LocalDateTime.now());
        refundRepository.save(refund);

        // Publish Kafka event
        kafkaTemplate.send(KafkaTopics.REFUND_REJECTED, String.valueOf(refundId),
                Map.of(
                    "refund_id", refundId,
                    "order_id", refund.getOrderId(),
                    "reject_reason", req.getRejectReason(),
                    "fraud_evidence", Boolean.TRUE.equals(req.getFraudEvidence()),
                    "admin_id", adminId,
                    "timestamp", Instant.now().toString()
                ));

        log.info("Refund rejected: refundId={}, adminId={}", refundId, adminId);
    }

    // ─── Internal helpers ─────────────────────────────────────────────

    private String executeStripeRefund(Long transactionId, BigDecimal amount) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Transaction không tồn tại"));

        if (tx.getStripePiId() == null) {
            log.warn("No Stripe PI ID for transaction {}, skipping Stripe refund call", transactionId);
            return "manual_refund_" + transactionId;
        }

        try {
            long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValue();
            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(tx.getStripePiId())
                    .setAmount(amountInCents)
                    .build();

            com.stripe.model.Refund stripeRefund = com.stripe.model.Refund.create(params);
            log.info("Stripe refund created: refundId={}, amount={}", stripeRefund.getId(), amountInCents);
            return stripeRefund.getId();

        } catch (StripeException e) {
            log.error("Stripe refund failed for transaction {}: {}", transactionId, e.getMessage());
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Stripe refund failed: " + e.getMessage());
        }
    }

    private RefundListResponse toListResponse(Refund r) {
        return RefundListResponse.builder()
                .refundId(r.getId())
                .refundCode(buildRefundCode(r))
                .orderId(r.getOrderId())
                .groupRef(r.getGroupRef())
                .type(r.getType())
                .status(r.getStatus())
                .amount(r.getAmount())
                .adjustAmount(r.getAdjustAmount())
                .initiatedBy(r.getInitiatedBy())
                .refundReasonType(r.getRefundReasonType())
                .adminNote(r.getAdminNote())
                .rejectReason(r.getRejectReason())
                .reviewedBy(r.getReviewedBy())
                .reviewedAt(r.getReviewedAt() != null ? r.getReviewedAt().toInstant(ZoneOffset.UTC) : null)
                .refundRef(r.getRefundRef())
                .createdAt(r.getCreatedAt().toInstant(ZoneOffset.UTC))
                .build();
    }

    private String buildRefundCode(Refund r) {
        return "RF-" + r.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + r.getId();
    }

    // ─── Kafka Consumers ──────────────────────────────────────────────

    @KafkaListener(topics = KafkaTopics.REFUND_REQUESTED, groupId = "payment-service-group")
    public void onRefundRequested(String message) {
        log.info("Refund requested event received: {}", message);
    }

    @KafkaListener(topics = KafkaTopics.REFUND_ADMIN_APPROVED, groupId = "payment-service-group")
    public void onRefundApproved(String message) {
        log.info("Refund admin approved event received: {}", message);
    }

    // ─── Legacy compat methods ────────────────────────────────────────

    public void requestRefund(Long orderId, String reason, String initiatedBy) {
        log.info("Requesting refund for order: {}, reason: {}", orderId, reason);
        Refund refund = Refund.builder()
            .orderId(orderId)
            .reason(reason)
            .initiatedBy(initiatedBy)
            .status("PENDING")
            .type("PARTIAL")
            .amount(BigDecimal.ZERO)
            .transactionId(0L)
            .build();
        refundRepository.save(refund);
    }
}
