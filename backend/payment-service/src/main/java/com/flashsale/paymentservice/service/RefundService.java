package com.flashsale.paymentservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.dto.PageResponse;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.paymentservice.domain.model.Refund;
import com.flashsale.paymentservice.domain.model.RefundItem;
import com.flashsale.paymentservice.domain.model.SellerTransfer;
import com.flashsale.paymentservice.domain.model.Transaction;
import com.flashsale.paymentservice.domain.repository.RefundItemRepository;
import com.flashsale.paymentservice.domain.repository.RefundRepository;
import com.flashsale.paymentservice.domain.repository.SellerTransferRepository;
import com.flashsale.paymentservice.domain.repository.TransactionRepository;
import com.flashsale.paymentservice.dto.request.AdminRefundApproveRequest;
import com.flashsale.paymentservice.dto.request.AdminRefundRejectRequest;
import com.flashsale.paymentservice.dto.response.AdminRefundApproveResponse;
import com.flashsale.paymentservice.dto.response.RefundDetailResponse;
import com.flashsale.paymentservice.dto.response.RefundListResponse;
import com.stripe.exception.StripeException;
import com.stripe.model.Transfer;
import com.stripe.model.TransferReversal;
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
import java.util.*;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundService {

    private final RefundRepository refundRepository;
    private final RefundItemRepository refundItemRepository;
    private final TransactionRepository transactionRepository;
    private final SellerTransferRepository sellerTransferRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // ─── Admin: List Refunds ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<RefundListResponse> listAllRefunds(
            String status, String type,
            String fromDate, String toDate,
            int page, int size) {

        LocalDateTime from = fromDate != null ? LocalDateTime.parse(fromDate + "T00:00:00") : null;
        LocalDateTime to   = toDate   != null ? LocalDateTime.parse(toDate   + "T23:59:59") : null;

        Page<Refund> result = refundRepository.findAllWithFilters(
                status, type, from, to,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        return PageResponse.<RefundListResponse>builder()
                .content(result.getContent().stream().map(this::toListResponse).collect(Collectors.toList()))
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build();
    }

    // ─── Admin: Get Refund Detail ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RefundDetailResponse getRefundById(Long refundId) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Refund không tồn tại: " + refundId));

        List<RefundItem> items = refundItemRepository.findAllByRefundId(refundId);

        // Hibernate maps jsonb to List<String> directly
        List<String> evidenceImages = refund.getEvidenceImages();

        // Build return evidence from RefundItems that have tracking
        List<RefundDetailResponse.ReturnEvidence> returnEvidence = items.stream()
                .filter(ri -> ri.getReturnTrackingNumber() != null)
                .map(ri -> RefundDetailResponse.ReturnEvidence.builder()
                        .type("tracking")
                        .trackingNumber(ri.getReturnTrackingNumber())
                        .recordedAt(ri.getReturnedAt() != null
                                ? ri.getReturnedAt().toInstant(ZoneOffset.UTC) : null)
                        .build())
                .collect(Collectors.toList());

        // Collect tracking number (first non-null)
        String trackingNumber = items.stream()
                .map(RefundItem::getReturnTrackingNumber)
                .filter(t -> t != null && !t.isBlank())
                .findFirst().orElse(null);

        List<RefundDetailResponse.RefundItemInfo> itemInfos = items.stream()
                .map(ri -> RefundDetailResponse.RefundItemInfo.builder()
                        .itemId(ri.getItemId())
                        .quantity(ri.getQuantity())
                        .refundAmount(ri.getRefundAmount())
                        .itemReason(ri.getItemReason())
                        .status(ri.getStatus())
                        .returnTrackingNumber(ri.getReturnTrackingNumber())
                        .returnedAt(ri.getReturnedAt() != null
                                ? ri.getReturnedAt().toInstant(ZoneOffset.UTC) : null)
                        .build())
                .collect(Collectors.toList());

        return RefundDetailResponse.builder()
                .refundId(refund.getId())
                .refundCode(buildRefundCode(refund))
                .orderId(refund.getOrderId())
                .groupRef(refund.getGroupRef() != null ? refund.getGroupRef().toString() : null)
                .type(refund.getType())
                .status(refund.getStatus())
                .amount(refund.getAmount())
                .reason(refund.getReason())
                .initiatedBy(refund.getInitiatedBy())
                .refundReasonType(refund.getRefundReasonType())
                .evidenceImages(evidenceImages)
                .adminNote(refund.getAdminNote())
                .rejectReason(refund.getRejectReason())
                .trackingNumber(trackingNumber)
                .returnEvidence(returnEvidence.isEmpty() ? null : returnEvidence)
                .reviewedBy(refund.getReviewedBy())
                .reviewedAt(refund.getReviewedAt() != null
                        ? refund.getReviewedAt().toInstant(ZoneOffset.UTC) : null)
                .stripeRefundId(refund.getRefundRef())
                .items(itemInfos)
                .createdAt(refund.getCreatedAt().toInstant(ZoneOffset.UTC))
                .updatedAt(refund.getUpdatedAt().toInstant(ZoneOffset.UTC))
                .build();
    }

    // ─── Admin: Approve Refund ───────────────────────────────────────────────

    @Transactional
    public AdminRefundApproveResponse approveRefund(Long refundId, Long adminId, AdminRefundApproveRequest req) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Refund không tồn tại: " + refundId));

        if (!"PENDING".equals(refund.getStatus()) && !"FAILED".equals(refund.getStatus())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Chỉ có thể approve refund ở trạng thái PENDING hoặc FAILED");
        }

        BigDecimal finalAmount = refund.getAmount();
        String stripeRefundId = executeStripeRefund(refund.getTransactionId(), finalAmount);

        // Reverse phần transfer tới Seller tương ứng với khoản hoàn tiền
        reverseSellerTransfer(refund.getOrderId(), finalAmount, refundId);

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
        refund.setReviewedBy(adminId);
        refund.setReviewedAt(LocalDateTime.now());
        refund.setRefundRef(stripeRefundId);
        refundRepository.save(refund);

        publish(KafkaTopics.REFUND_ADMIN_APPROVED, String.valueOf(refundId), Map.of(
                "refund_id",       refundId,
                "order_id",        refund.getOrderId(),
                "amount",          finalAmount,
                "type",            refund.getType(),
                "admin_id",        adminId,
                "caused_by",       req.getCausedBy() != null ? req.getCausedBy() : "",
                "tracking_number", req.getTrackingNumber() != null ? req.getTrackingNumber() : "",
                "timestamp",       Instant.now().toString()
        ));

        log.info("Refund approved: refundId={}, adminId={}, amount={}, stripeRefundId={}",
                refundId, adminId, finalAmount, stripeRefundId);

        return AdminRefundApproveResponse.builder()
                .refundId(refund.getId())
                .refundCode(buildRefundCode(refund))
                .status("SUCCESS")
                .type(refund.getType())
                .amount(finalAmount)
                .trackingNumber(req.getTrackingNumber())
                .returnEvidence(returnEvidence.isEmpty() ? null : returnEvidence)
                .reviewedBy(adminId)
                .adminNote(req.getAdminNote())
                .reviewedAt(refund.getReviewedAt().toInstant(ZoneOffset.UTC))
                .stripeRefundId(stripeRefundId)
                .build();
    }

    // ─── Admin: Reject Refund ────────────────────────────────────────────────

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

        publish(KafkaTopics.REFUND_REJECTED, String.valueOf(refundId), Map.of(
                "refund_id",      refundId,
                "order_id",       refund.getOrderId(),
                "user_id",        0L,
                "reject_reason",  req.getRejectReason(),
                "fraud_evidence", Boolean.TRUE.equals(req.getFraudEvidence()),
                "admin_id",       adminId,
                "timestamp",      Instant.now().toString()
        ));

        log.info("Refund rejected: refundId={}, adminId={}", refundId, adminId);
    }

    // ─── Kafka Consumer: REFUND_REQUESTED (Buyer Partial Refund) ────────────

    /**
     * Nhận event từ order-service khi Buyer yêu cầu hoàn tiền một phần.
     * Tạo Refund + RefundItems trong DB. Không cần Admin duyệt ở đây
     * — Stripe sẽ chỉ chạy sau khi Admin approve (xem approveRefund).
     *
     * Payload từ order-service:
     * { refund_type, order_id, parent_order_id, user_id, seller_id,
     *   reason, amount, group_ref, refund_reason_type, items[], evidence_images[], timestamp }
     */
    @KafkaListener(topics = KafkaTopics.REFUND_REQUESTED, groupId = "payment-service-group")
    @Transactional
    public void onRefundRequested(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            Long orderId       = toLong(payload.get("order_id"));
            Long parentOrderId = toLong(payload.get("parent_order_id"));
            Long userId        = toLong(payload.get("user_id"));
            UUID groupRef      = parseUUID(payload.get("group_ref"));
            String reason      = (String) payload.get("reason");
            BigDecimal amount  = toBigDecimal(payload.get("amount"));
            String reasonType  = (String) payload.get("refund_reason_type");

            // Idempotency: bỏ qua nếu đã có PENDING refund cho đơn này
            if (refundRepository.existsByOrderIdAndStatusIn(orderId, List.of("PENDING", "SUCCESS"))) {
                log.warn("Duplicate REFUND_REQUESTED for orderId={}, skipping", orderId);
                return;
            }

            // Tìm transaction theo parentOrderId
            Transaction tx = transactionRepository.findByParentOrderId(parentOrderId)
                    .orElse(null);
            if (tx == null) {
                log.error("Transaction not found for parentOrderId={}, cannot create refund", parentOrderId);
                return;
            }

            // Hibernate maps List<String> to jsonb directly
            List<String> evidenceImages = toStringList(payload.get("evidence_images"));

            Refund refund = Refund.builder()
                    .transactionId(tx.getId())
                    .orderId(orderId)
                    .groupRef(groupRef)
                    .type("PARTIAL")
                    .initiatedBy("BUYER")
                    .refundReasonType(reasonType)
                    .amount(amount)
                    .reason(reason)
                    .status("PENDING")
                    .evidenceImages(evidenceImages.isEmpty() ? null : evidenceImages)
                    .build();
            refund = refundRepository.save(refund);

            // Tạo RefundItems
            List<?> items = (List<?>) payload.get("items");
            if (items != null) {
                for (Object raw : items) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> item = (Map<String, Object>) raw;
                    RefundItem ri = RefundItem.builder()
                            .refundId(refund.getId())
                            .itemId(toLong(item.get("order_item_id")))
                            .quantity(toInt(item.get("quantity")))
                            .refundAmount(toBigDecimal(item.get("refund_amount")))
                            .itemReason((String) item.get("item_reason"))
                            .status("PENDING")
                            .build();
                    refundItemRepository.save(ri);
                }
            }

            // Notify notification-service rằng refund record đã được tạo
            publish(KafkaTopics.REFUND_CREATED, String.valueOf(refund.getId()), Map.of(
                    "refund_id", refund.getId(),
                    "order_id",  orderId,
                    "user_id",   userId,
                    "amount",    amount,
                    "type",      "PARTIAL",
                    "status",    "PENDING",
                    "timestamp", Instant.now().toString()
            ));

            log.info("Refund created from REFUND_REQUESTED: refundId={}, orderId={}, amount={}",
                    refund.getId(), orderId, amount);

        } catch (Exception e) {
            log.error("Error processing REFUND_REQUESTED: {}", e.getMessage(), e);
        }
    }

    // ─── Kafka Consumer: REFUND_FULL_REQUESTED (Buyer Full Refund) ──────────

    /**
     * Nhận event từ order-service khi Buyer yêu cầu hoàn tiền toàn bộ đơn cha.
     * Tạo N Refund records (N = số sub-orders), cùng group_ref.
     *
     * Payload:
     * { parent_order_id, user_id, group_ref, total_amount,
     *   refunds: [{ order_id, seller_id, amount, item_count }], timestamp }
     */
    @KafkaListener(topics = KafkaTopics.REFUND_FULL_REQUESTED, groupId = "payment-service-group")
    @Transactional
    public void onRefundFullRequested(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            Long parentOrderId = toLong(payload.get("parent_order_id"));
            Long userId        = toLong(payload.get("user_id"));
            UUID groupRef      = parseUUID(payload.get("group_ref"));

            Transaction tx = transactionRepository.findByParentOrderId(parentOrderId)
                    .orElse(null);
            if (tx == null) {
                log.error("Transaction not found for parentOrderId={}, cannot create full refund", parentOrderId);
                return;
            }

            List<?> refundList = (List<?>) payload.get("refunds");
            if (refundList == null || refundList.isEmpty()) return;

            List<Long> createdIds = new ArrayList<>();
            for (Object raw : refundList) {
                @SuppressWarnings("unchecked")
                Map<String, Object> r = (Map<String, Object>) raw;
                Long orderId  = toLong(r.get("order_id"));
                BigDecimal amt = toBigDecimal(r.get("amount"));

                if (refundRepository.existsByOrderIdAndStatusIn(orderId, List.of("PENDING", "SUCCESS"))) {
                    log.warn("Duplicate REFUND_FULL_REQUESTED for orderId={}, skipping sub-order", orderId);
                    continue;
                }

                Refund refund = Refund.builder()
                        .transactionId(tx.getId())
                        .orderId(orderId)
                        .groupRef(groupRef)
                        .type("FULL")
                        .initiatedBy("BUYER")
                        .refundReasonType("BUYER_CANCEL")
                        .amount(amt)
                        .reason("Full refund requested by buyer")
                        .status("PENDING")
                        .build();
                refund = refundRepository.save(refund);
                createdIds.add(refund.getId());
            }

            log.info("Full refund created from REFUND_FULL_REQUESTED: parentOrderId={}, refundCount={}",
                    parentOrderId, createdIds.size());

        } catch (Exception e) {
            log.error("Error processing REFUND_FULL_REQUESTED: {}", e.getMessage(), e);
        }
    }

    // ─── Kafka Consumer: ORDER_RETURNED_RTS (Auto Refund, No Admin needed) ──

    /**
     * Nhận event từ order-service khi Seller xác nhận hàng hoàn (RTS).
     * Hoàn tiền tự động qua Stripe, KHÔNG cần Admin duyệt.
     *
     * Payload từ order-service.publishOrderReturned():
     * { order_id, parent_order_id, user_id, seller_id, refund_reason_type,
     *   return_tracking_number, total_amount, evidence_count, timestamp }
     */
    @KafkaListener(topics = KafkaTopics.ORDER_RETURNED_RTS, groupId = "payment-service-group")
    @Transactional
    public void onOrderReturnedRts(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            Long orderId       = toLong(payload.get("order_id"));
            Long parentOrderId = toLong(payload.get("parent_order_id"));
            Long userId        = toLong(payload.get("user_id"));
            BigDecimal amount  = toBigDecimal(payload.get("total_amount"));
            String returnTracking = (String) payload.get("return_tracking_number");

            // Idempotency
            if (refundRepository.existsByOrderIdAndStatusIn(orderId, List.of("PENDING", "SUCCESS"))) {
                log.warn("RTS refund already exists for orderId={}, skipping", orderId);
                return;
            }

            Transaction tx = transactionRepository.findByParentOrderId(parentOrderId)
                    .orElse(null);
            if (tx == null) {
                log.error("Transaction not found for parentOrderId={} (RTS), cannot refund", parentOrderId);
                return;
            }

            // Tạo Refund record
            Refund refund = Refund.builder()
                    .transactionId(tx.getId())
                    .orderId(orderId)
                    .type("FULL")
                    .initiatedBy("SELLER")
                    .refundReasonType("RETURN_TO_SENDER")
                    .amount(amount)
                    .reason("Hàng hoàn về Seller (Return To Sender)")
                    .status("PENDING")
                    .build();
            refund = refundRepository.save(refund);

            // Cập nhật tracking trên RefundItem nếu có
            if (returnTracking != null && !returnTracking.isBlank()) {
                RefundItem ri = RefundItem.builder()
                        .refundId(refund.getId())
                        .itemId(orderId) // placeholder — all items of this order
                        .quantity(1)
                        .refundAmount(amount)
                        .itemReason("Return To Sender")
                        .status("PENDING")
                        .returnTrackingNumber(returnTracking)
                        .build();
                refundItemRepository.save(ri);
            }

            // Thực hiện Stripe refund tự động
            String stripeRefundId;
            try {
                stripeRefundId = executeStripeRefund(tx.getId(), amount);
                refund.setStatus("SUCCESS");
                refund.setRefundRef(stripeRefundId);
                refund.setReviewedAt(LocalDateTime.now());

                // Reverse toàn bộ transfer đã gửi cho Seller (RTS = full refund)
                reverseSellerTransfer(orderId, amount, refund.getId());
            } catch (AppException e) {
                log.error("Stripe refund failed for RTS orderId={}: {}", orderId, e.getMessage());
                refund.setStatus("FAILED");
                stripeRefundId = null;
            }
            refundRepository.save(refund);

            // Publish REFUND_RTS_COMPLETED
            Map<String, Object> event = new HashMap<>();
            event.put("refund_id",    refund.getId());
            event.put("order_id",     orderId);
            event.put("user_id",      userId);
            event.put("amount",       amount);
            event.put("status",       refund.getStatus());
            event.put("stripe_refund_id", stripeRefundId != null ? stripeRefundId : "");
            event.put("timestamp",    Instant.now().toString());
            publish(KafkaTopics.REFUND_RTS_COMPLETED, String.valueOf(orderId), event);

            log.info("RTS refund processed: refundId={}, orderId={}, status={}, stripeRefundId={}",
                    refund.getId(), orderId, refund.getStatus(), stripeRefundId);

        } catch (Exception e) {
            log.error("Error processing ORDER_RETURNED_RTS: {}", e.getMessage(), e);
        }
    }

    // ─── Kafka Consumer: ORDER_REFUNDS_REQUEST (Reply handler for order-service) ──

    /**
     * order-service gửi request để lấy danh sách refunds theo orderId hoặc userId.
     *
     * Request modes:
     * - { correlation_id, order_id } → lấy refunds của 1 sub-order
     * - { correlation_id, user_id, status?, from_date?, to_date?, page, size } → lấy refunds của Buyer
     */
    @KafkaListener(topics = KafkaTopics.ORDER_REFUNDS_REQUEST, groupId = "payment-service-reply-group")
    public void onOrderRefundsRequest(String message) {
        String correlationId = null;
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            correlationId = (String) payload.get("correlation_id");
            if (correlationId == null) return;

            List<Map<String, Object>> refundData;
            long totalElements = 0;
            int totalPages = 1;

            if (payload.containsKey("order_id")) {
                // Mode 1: refunds cho 1 sub-order
                Long orderId = toLong(payload.get("order_id"));
                List<Refund> refunds = refundRepository.findAllByOrderId(orderId);
                refundData = refunds.stream().map(this::toRefundMap).collect(Collectors.toList());
                totalElements = refundData.size();

            } else if (payload.containsKey("user_id")) {
                // Mode 2: tất cả refunds của Buyer
                Long userId    = toLong(payload.get("user_id"));
                String status  = (String) payload.get("status");
                String type    = (String) payload.get("type");
                int page       = toInt(payload.getOrDefault("page", 0));
                int size       = toInt(payload.getOrDefault("size", 20));
                String fromStr = (String) payload.get("from_date");
                String toStr   = (String) payload.get("to_date");

                LocalDateTime from = fromStr != null ? LocalDateTime.parse(fromStr + "T00:00:00") : null;
                LocalDateTime to   = toStr   != null ? LocalDateTime.parse(toStr   + "T23:59:59") : null;

                Page<Refund> pageResult = refundRepository.findAllByUserIdWithFilters(
                        userId, status, type, from, to,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
                refundData = pageResult.getContent().stream().map(this::toRefundMap).collect(Collectors.toList());
                totalElements = pageResult.getTotalElements();
                totalPages    = pageResult.getTotalPages();

            } else {
                refundData = List.of();
            }

            Map<String, Object> response = new HashMap<>();
            response.put("correlation_id",  correlationId);
            response.put("refunds",          refundData);
            response.put("total_elements",   totalElements);
            response.put("total_pages",      totalPages);

            kafkaTemplate.send(KafkaTopics.ORDER_REFUNDS_RESPONSE, correlationId, toJson(response));

        } catch (Exception e) {
            log.error("Error processing ORDER_REFUNDS_REQUEST: {}", e.getMessage(), e);
            if (correlationId != null) {
                Map<String, Object> errorResp = Map.of(
                        "correlation_id", correlationId,
                        "error", true,
                        "refunds", List.of()
                );
                kafkaTemplate.send(KafkaTopics.ORDER_REFUNDS_RESPONSE, correlationId, toJson(errorResp));
            }
        }
    }

    // ─── Kafka Consumer: ORDER_PAYMENT_STATUS_REQUEST ───────────────────────

    /**
     * order-service hỏi transaction status theo parentOrderId.
     * Dùng để order-service kiểm tra xem đơn đã thanh toán chưa trước khi tạo refund.
     */
    @KafkaListener(topics = KafkaTopics.ORDER_PAYMENT_STATUS_REQUEST, groupId = "payment-service-reply-group")
    public void onOrderPaymentStatusRequest(String message) {
        String correlationId = null;
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            correlationId = (String) payload.get("correlation_id");
            if (correlationId == null) return;
            Long parentOrderId = toLong(payload.get("parent_order_id"));
            Optional<Transaction> txOpt = transactionRepository.findByParentOrderId(parentOrderId);

            Map<String, Object> response = new HashMap<>();
            response.put("correlation_id",   correlationId);
            response.put("parent_order_id",  parentOrderId);
            if (txOpt.isPresent()) {
                Transaction tx = txOpt.get();
                response.put("transaction_id", tx.getId());
                response.put("stripe_pi_id",   null);
                response.put("status",         tx.getStatus());
                response.put("amount",         tx.getAmount());
                response.put("error",          false);
            } else {
                response.put("error",  true);
                response.put("status", "NOT_FOUND");
            }

            kafkaTemplate.send(KafkaTopics.ORDER_PAYMENT_STATUS_RESPONSE, correlationId, toJson(response));

        } catch (Exception e) {
            log.error("Error processing ORDER_PAYMENT_STATUS_REQUEST: {}", e.getMessage(), e);
            kafkaTemplate.send(KafkaTopics.ORDER_PAYMENT_STATUS_RESPONSE, correlationId,
                    toJson(Map.of("correlation_id", correlationId, "error", true)));
        }
    }

    // ─── Internal helpers ────────────────────────────────────────────────────

    /**
     * Reverse phần transfer đã gửi cho Seller tương ứng với khoản hoàn tiền.
     *
     * Stripe Connect: khi refund Buyer qua PaymentIntent, platform balance bị trừ,
     * nhưng transfer đến Seller không tự động bị reverse — phải gọi API riêng.
     *
     * Công thức:
     *   reversal_amount = refundAmount / transferAmount * netAmount  (proportional)
     *   Nếu refundAmount >= transferAmount → reverse toàn bộ netAmount.
     *
     * @return Stripe TransferReversal ID hoặc null nếu không thể reverse
     */
    private String reverseSellerTransfer(Long orderId, BigDecimal refundAmount, Long refundId) {
        SellerTransfer st = sellerTransferRepository.findByOrderId(orderId).orElse(null);

        if (st == null) {
            log.warn("reverseSellerTransfer: no SellerTransfer for orderId={}", orderId);
            return null;
        }

        // New: if transfer hasn't been paid out yet, no stripe reversal needed.
        // Funds are still in the platform balance — just mark the transfer as REFUNDED
        // so the payout cron won't pick it up later.
        String transferStatus = st.getStatus();
        if ("AWAITING_DELIVERY".equals(transferStatus)
                || "RETURN_WINDOW".equals(transferStatus)
                || "READY_FOR_PAYOUT".equals(transferStatus)) {
            log.info("reverseSellerTransfer: transfer not yet paid (status={}) for orderId={}, marking REFUNDED", transferStatus, orderId);
            st.setStatus("REFUNDED");
            sellerTransferRepository.save(st);
            return null;
        }
        if (st.getStripeTransferId() == null) {
            // Seller chưa có Stripe account khi thanh toán → transfer bị SKIPPED → không cần reverse
            log.info("reverseSellerTransfer: no stripeTransferId for orderId={} (status={}), skipping", orderId, st.getStatus());
            return null;
        }
        if ("REVERSED".equals(st.getStatus())) {
            log.info("reverseSellerTransfer: transfer already REVERSED for orderId={}", orderId);
            return null;
        }

        BigDecimal transferAmount = st.getTransferAmount() != null ? st.getTransferAmount() : BigDecimal.ZERO;
        BigDecimal netAmount      = transferAmount;

        if (netAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("reverseSellerTransfer: netAmount is zero for orderId={}, skipping", orderId);
            return null;
        }

        // Tính reversal amount theo tỷ lệ
        boolean fullReversal = transferAmount.compareTo(BigDecimal.ZERO) <= 0
                || refundAmount.compareTo(transferAmount) >= 0;

        long reversalAmount;
        if (fullReversal) {
            reversalAmount = netAmount.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        } else {
            reversalAmount = refundAmount
                    .multiply(netAmount)
                    .divide(transferAmount, 0, java.math.RoundingMode.HALF_UP)
                    .min(netAmount)   // cap tại netAmount
                    .longValue();
        }

        if (reversalAmount <= 0) return null;

        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("refund_id", String.valueOf(refundId));
            metadata.put("order_id",  String.valueOf(orderId));

            Map<String, Object> params = new HashMap<>();
            params.put("amount",   reversalAmount);
            params.put("metadata", metadata);

            Transfer transfer = Transfer.retrieve(st.getStripeTransferId());
            TransferReversal reversal = transfer.getReversals().create(params);

            st.setStatus(fullReversal ? "REVERSED" : "PARTIALLY_REVERSED");
            sellerTransferRepository.save(st);

            log.info("Seller transfer reversed: orderId={}, transferId={}, reversalId={}, amount={}, full={}",
                    orderId, st.getStripeTransferId(), reversal.getId(), reversalAmount, fullReversal);
            return reversal.getId();

        } catch (StripeException e) {
            // Non-fatal: buyer đã được refund; cần manual reconcile phía seller
            log.error("Failed to reverse seller transfer for orderId={}, transferId={}: {}",
                    orderId, st.getStripeTransferId(), e.getMessage());
            return null;
        }
    }

    private String executeStripeRefund(Long transactionId, BigDecimal amount) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Transaction không tồn tại"));

        // PI ID was removed from entity; extract from rawResponse JSON
        String stripePiId = extractPiIdFromRawResponse(tx.getRawResponse());
        if (stripePiId == null) {
            log.warn("No Stripe PI ID for transaction {}, skipping Stripe refund call", transactionId);
            return "manual_refund_" + transactionId;
        }

        try {
            // VND is a zero-decimal currency in Stripe — amount is already in the smallest unit (no ×100)
            long stripeAmount = amount.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(stripePiId)
                    .setAmount(stripeAmount)
                    .build();
            com.stripe.model.Refund stripeRefund = com.stripe.model.Refund.create(params);
            log.info("Stripe refund created: refundId={}, amount={}", stripeRefund.getId(), stripeAmount);
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
                .groupRef(r.getGroupRef() != null ? r.getGroupRef().toString() : null)
                .type(r.getType())
                .status(r.getStatus())
                .amount(r.getAmount())
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

    /**
     * Serialize Refund thành Map để gửi qua Kafka reply.
     */
    private Map<String, Object> toRefundMap(Refund r) {
        Map<String, Object> m = new HashMap<>();
        m.put("refund_id",          r.getId());
        m.put("refund_code",        buildRefundCode(r));
        m.put("order_id",           r.getOrderId());
        m.put("group_ref",          r.getGroupRef());
        m.put("type",               r.getType());
        m.put("status",             r.getStatus());
        m.put("amount",             r.getAmount());
        m.put("adjust_amount",      null);
        m.put("reason",             r.getReason());
        m.put("refund_reason_type", r.getRefundReasonType());
        m.put("initiated_by",       r.getInitiatedBy());
        m.put("admin_note",         r.getAdminNote());
        m.put("reject_reason",      r.getRejectReason());
        m.put("reviewed_by",        r.getReviewedBy());
        m.put("reviewed_at",        r.getReviewedAt() != null ? r.getReviewedAt().toInstant(ZoneOffset.UTC).toString() : null);
        m.put("refund_ref",         r.getRefundRef());
        m.put("created_at",         r.getCreatedAt().toInstant(ZoneOffset.UTC).toString());
        return m;
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

    private String buildRefundCode(Refund r) {
        return "RF-" + r.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + r.getId();
    }

    // ─── Kafka helpers ────────────────────────────────────────────────────────

    private void publish(String topic, String key, Object payload) {
        try {
            kafkaTemplate.send(topic, key, objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize Kafka payload for topic {}: {}", topic, e.getMessage());
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize to JSON: {}", e.getMessage());
            return "{}";
        }
    }

    // ─── Type conversion helpers ──────────────────────────────────────────────

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Long l) return l;
        if (v instanceof Integer i) return i.longValue();
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return null; }
    }

    private int toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Integer i) return i;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return 0; }
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(v.toString()); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private UUID parseUUID(Object v) {
        if (v == null) return null;
        if (v instanceof UUID u) return u;
        try { return UUID.fromString(v.toString()); } catch (Exception e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object v) {
        if (v == null) return List.of();
        if (v instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.toList());
        }
        return List.of();
    }
}
