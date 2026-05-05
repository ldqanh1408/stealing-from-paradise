package com.flashsale.orderservice.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.orderservice.domain.model.Order;
import com.flashsale.orderservice.domain.model.OrderItem;
import com.flashsale.orderservice.domain.model.ParentOrder;
import com.flashsale.orderservice.domain.repository.OrderItemRepository;
import com.flashsale.orderservice.domain.repository.OrderRepository;
import com.flashsale.orderservice.domain.repository.ParentOrderRepository;
import com.flashsale.orderservice.dto.request.BuyerPartialRefundItem;
import com.flashsale.orderservice.dto.request.BuyerPartialRefundRequest;
import com.flashsale.orderservice.dto.request.FullRefundRequest;
import com.flashsale.orderservice.dto.response.FullRefundCreatedResponse;
import com.flashsale.orderservice.dto.response.OrderRefundInfo;
import com.flashsale.orderservice.dto.response.PresignedUrlResponse;
import com.flashsale.orderservice.dto.response.RefundCreatedResponse;
import com.flashsale.orderservice.service.KafkaReplyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Slf4j
public class RefundController {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ParentOrderRepository parentOrderRepository;
    private final KafkaReplyService kafkaReplyService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // ─── POST /orders/{orderId}/refunds — Partial Refund (1 seller) ──────────

    /**
     * Buyer yêu cầu hoàn tiền một phần cho 1 sub-order.
     * Điều kiện: order.status IN [PAID, SHIPPING, DELIVERED, PARTIALLY_REFUNDED]
     * Nếu DELIVERED: trong vòng 7 ngày kể từ deliveredAt.
     */
    @PostMapping("/orders/{orderId}/refunds")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ApiResponse<RefundCreatedResponse>> createPartialRefund(
            @PathVariable Long orderId,
            @Valid @RequestBody BuyerPartialRefundRequest req,
            @AuthenticationPrincipal UserDetailsImpl user) {

        Long userId = user.getId();

        // 1. Lấy và validate order
        Order order = orderRepository.findByIdAndCustomerId(orderId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN,
                        "Đơn hàng không tồn tại hoặc không thuộc về bạn"));

        validateOrderStatusForRefund(order);

        // 2. Validate items và tính amount
        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(orderId);
        Map<Long, OrderItem> itemMap = orderItems.stream()
                .collect(Collectors.toMap(OrderItem::getId, i -> i));

        BigDecimal totalRefundAmount = BigDecimal.ZERO;
        List<Map<String, Object>> refundItems = new ArrayList<>();

        for (BuyerPartialRefundItem reqItem : req.getItems()) {
            OrderItem oi = itemMap.get(reqItem.getOrderItemId());
            if (oi == null) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Item " + reqItem.getOrderItemId() + " không thuộc đơn hàng này");
            }
            int refunded = oi.getRefundedQuantity() != null ? oi.getRefundedQuantity() : 0;
            int available = oi.getQuantity() - refunded;
            if (reqItem.getQuantity() > available) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Số lượng hoàn của item " + reqItem.getOrderItemId()
                                + " vượt quá số lượng khả dụng (" + available + ")");
            }
            BigDecimal itemAmt = oi.getPriceSnapshot().multiply(BigDecimal.valueOf(reqItem.getQuantity()));
            totalRefundAmount = totalRefundAmount.add(itemAmt);

            Map<String, Object> ri = new HashMap<>();
            ri.put("order_item_id", reqItem.getOrderItemId());
            ri.put("quantity", reqItem.getQuantity());
            ri.put("refund_amount", itemAmt);
            ri.put("item_reason", reqItem.getItemReason() != null ? reqItem.getItemReason() : "");
            refundItems.add(ri);
        }

        // 3. Publish REFUND_REQUESTED → payment-service tạo Refund record
        String groupRef = UUID.randomUUID().toString();
        Map<String, Object> event = new HashMap<>();
        event.put("refund_type",        "PARTIAL");
        event.put("order_id",           orderId);
        event.put("parent_order_id",    order.getParentOrderId());
        event.put("user_id",            userId);
        event.put("seller_id",          order.getSellerId());
        event.put("reason",             req.getReason());
        event.put("amount",             totalRefundAmount);
        event.put("group_ref",          groupRef);
        event.put("refund_reason_type", "BUYER_REQUEST");
        event.put("items",              refundItems);
        event.put("evidence_images",    req.getEvidenceImages() != null ? req.getEvidenceImages() : List.of());
        event.put("timestamp",          Instant.now().toString());
        kafkaTemplate.send(KafkaTopics.REFUND_REQUESTED, String.valueOf(orderId), toJson(event));

        log.info("Partial refund requested: orderId={}, userId={}, amount={}", orderId, userId, totalRefundAmount);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                RefundCreatedResponse.builder()
                        .groupRef(groupRef)
                        .orderId(orderId)
                        .type("PARTIAL")
                        .status("PENDING")
                        .totalAmount(order.getFinalAmt())
                        .refundAmount(totalRefundAmount)
                        .itemCount(req.getItems().size())
                        .items(refundItems)
                        .evidenceImages(req.getEvidenceImages())
                        .estimatedDays(3)
                        .message("Yêu cầu hoàn tiền đã được ghi nhận và đang chờ xử lý")
                        .createdAt(Instant.now())
                        .build(),
                "Refund request submitted"
        ));
    }

    // ─── POST /orders/parent/{parentOrderId}/refund — Full Refund ────────────

    /**
     * Buyer yêu cầu hoàn tiền toàn bộ đơn cha.
     * Điều kiện: tất cả sub-orders phải đang ở trạng thái DELIVERED trong vòng 7 ngày.
     */
    @PostMapping("/orders/parent/{parentOrderId}/refund")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ApiResponse<FullRefundCreatedResponse>> createFullRefund(
            @PathVariable Long parentOrderId,
            @Valid @RequestBody FullRefundRequest req,
            @AuthenticationPrincipal UserDetailsImpl user) {

        Long userId = user.getId();

        // 1. Validate parent order
        parentOrderRepository.findByIdAndCustomerId(parentOrderId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN,
                        "Đơn cha không tồn tại hoặc không thuộc về bạn"));

        // 2. Lấy tất cả sub-orders
        List<Order> subOrders = orderRepository.findAllByParentOrderId(parentOrderId);
        if (subOrders.isEmpty()) {
            throw new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn con nào");
        }

        // 3. Kiểm tra tất cả đơn con phải DELIVERED (trong vòng 7 ngày)
        for (Order o : subOrders) {
            validateOrderStatusForRefund(o);
        }

        // 4. Build per-seller refund data
        String groupRef = UUID.randomUUID().toString();
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<Map<String, Object>> refundList = new ArrayList<>();
        List<FullRefundCreatedResponse.SubRefundInfo> subInfos = new ArrayList<>();

        for (Order o : subOrders) {
            int itemCount = orderItemRepository.findAllByOrderId(o.getId()).size();
            refundList.add(Map.of(
                    "order_id",   o.getId(),
                    "seller_id",  o.getSellerId(),
                    "amount",     o.getFinalAmt(),
                    "item_count", itemCount
            ));
            totalAmount = totalAmount.add(o.getFinalAmt());
            subInfos.add(FullRefundCreatedResponse.SubRefundInfo.builder()
                    .orderId(o.getId())
                    .sellerId(o.getSellerId())
                    .amount(o.getFinalAmt())
                    .itemCount(itemCount)
                    .status("PENDING")
                    .build());
        }

        // 5. Publish REFUND_FULL_REQUESTED
        Map<String, Object> event = new HashMap<>();
        event.put("parent_order_id",  parentOrderId);
        event.put("user_id",          userId);
        event.put("group_ref",        groupRef);
        event.put("reason",           req.getReason());
        event.put("total_amount",     totalAmount);
        event.put("refunds",          refundList);
        event.put("evidence_images",  req.getEvidenceImages() != null ? req.getEvidenceImages() : List.of());
        event.put("refund_reason_type", "BUYER_REQUEST");
        event.put("timestamp",        Instant.now().toString());
        kafkaTemplate.send(KafkaTopics.REFUND_FULL_REQUESTED, String.valueOf(parentOrderId), toJson(event));

        log.info("Full refund requested: parentOrderId={}, userId={}, total={}", parentOrderId, userId, totalAmount);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                FullRefundCreatedResponse.builder()
                        .groupRef(groupRef)
                        .parentOrderId(parentOrderId)
                        .type("FULL")
                        .totalAmount(totalAmount)
                        .status("PENDING")
                        .refunds(subInfos)
                        .estimatedDays(3)
                        .message("Yêu cầu hoàn tiền toàn bộ đã được ghi nhận")
                        .createdAt(Instant.now())
                        .build(),
                "Full refund request submitted"
        ));
    }

    // ─── POST /orders/parent/{parentOrderId}/refunds/partial — Multi-Seller Partial ──

    /**
     * Buyer yêu cầu hoàn tiền một phần trên nhiều sub-orders (nhiều seller).
     * Server tự nhóm items theo sub-order và tạo N REFUND_REQUESTED events.
     */
    @PostMapping("/orders/parent/{parentOrderId}/refunds/partial")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ApiResponse<FullRefundCreatedResponse>> createMultiSellerPartialRefund(
            @PathVariable Long parentOrderId,
            @Valid @RequestBody BuyerPartialRefundRequest req,
            @AuthenticationPrincipal UserDetailsImpl user) {

        Long userId = user.getId();

        parentOrderRepository.findByIdAndCustomerId(parentOrderId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN,
                        "Đơn cha không tồn tại hoặc không thuộc về bạn"));

        // Tải tất cả sub-orders và items của parentOrder
        List<Order> subOrders = orderRepository.findAllByParentOrderId(parentOrderId);
        Map<Long, Order> itemIdToOrder = new HashMap<>();
        for (Order o : subOrders) {
            for (OrderItem oi : orderItemRepository.findAllByOrderId(o.getId())) {
                itemIdToOrder.put(oi.getId(), o);
            }
        }

        // Nhóm request items theo sub-order
        Map<Long, List<BuyerPartialRefundItem>> byOrder = new LinkedHashMap<>();
        for (BuyerPartialRefundItem ri : req.getItems()) {
            Order o = itemIdToOrder.get(ri.getOrderItemId());
            if (o == null) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Item " + ri.getOrderItemId() + " không thuộc parent order này");
            }
            validateOrderStatusForRefund(o);
            byOrder.computeIfAbsent(o.getId(), k -> new ArrayList<>()).add(ri);
        }

        String groupRef = UUID.randomUUID().toString();
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<FullRefundCreatedResponse.SubRefundInfo> subInfos = new ArrayList<>();

        // Tải items để tính giá
        Map<Long, OrderItem> allItems = new HashMap<>();
        for (Order o : subOrders) {
            orderItemRepository.findAllByOrderId(o.getId())
                    .forEach(oi -> allItems.put(oi.getId(), oi));
        }

        for (Map.Entry<Long, List<BuyerPartialRefundItem>> entry : byOrder.entrySet()) {
            Long orderId = entry.getKey();
            Order order  = subOrders.stream().filter(o -> o.getId().equals(orderId)).findFirst().orElseThrow();
            List<BuyerPartialRefundItem> reqItems = entry.getValue();

            BigDecimal subAmt = BigDecimal.ZERO;
            List<Map<String, Object>> refundItems = new ArrayList<>();
            for (BuyerPartialRefundItem ri : reqItems) {
                OrderItem oi = allItems.get(ri.getOrderItemId());
                int refunded = oi.getRefundedQuantity() != null ? oi.getRefundedQuantity() : 0;
                if (ri.getQuantity() > oi.getQuantity() - refunded) {
                    throw new AppException(ErrorCode.VALIDATION_FAILED,
                            "Số lượng hoàn của item " + ri.getOrderItemId() + " vượt quá khả dụng");
                }
                BigDecimal amt = oi.getPriceSnapshot().multiply(BigDecimal.valueOf(ri.getQuantity()));
                subAmt = subAmt.add(amt);

                Map<String, Object> rim = new HashMap<>();
                rim.put("order_item_id", ri.getOrderItemId());
                rim.put("quantity",      ri.getQuantity());
                rim.put("refund_amount", amt);
                rim.put("item_reason",   ri.getItemReason() != null ? ri.getItemReason() : "");
                refundItems.add(rim);
            }

            totalAmount = totalAmount.add(subAmt);

            Map<String, Object> event = new HashMap<>();
            event.put("refund_type",        "PARTIAL");
            event.put("order_id",           orderId);
            event.put("parent_order_id",    parentOrderId);
            event.put("user_id",            userId);
            event.put("seller_id",          order.getSellerId());
            event.put("reason",             req.getReason());
            event.put("amount",             subAmt);
            event.put("group_ref",          groupRef);
            event.put("refund_reason_type", "BUYER_REQUEST");
            event.put("items",              refundItems);
            event.put("evidence_images",    req.getEvidenceImages() != null ? req.getEvidenceImages() : List.of());
            event.put("timestamp",          Instant.now().toString());
            kafkaTemplate.send(KafkaTopics.REFUND_REQUESTED, String.valueOf(orderId), toJson(event));

            subInfos.add(FullRefundCreatedResponse.SubRefundInfo.builder()
                    .orderId(orderId)
                    .sellerId(order.getSellerId())
                    .amount(subAmt)
                    .itemCount(reqItems.size())
                    .build());
        }

        log.info("Multi-seller partial refund requested: parentOrderId={}, userId={}, sellerCount={}",
                parentOrderId, userId, byOrder.size());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                FullRefundCreatedResponse.builder()
                        .groupRef(groupRef)
                        .parentOrderId(parentOrderId)
                        .type("PARTIAL")
                        .totalAmount(totalAmount)
                        .status("PENDING")
                        .refunds(subInfos)
                        .message("Yêu cầu hoàn tiền đã được ghi nhận")
                        .createdAt(Instant.now())
                        .build()
        ));
    }

    // ─── GET /orders/{orderId}/refunds ───────────────────────────────────────

    /**
     * Lịch sử hoàn tiền của 1 sub-order.
     * Accessible by: Buyer (owner) | Seller (owner) | Admin
     */
    @GetMapping("/orders/{orderId}/refunds")
    @PreAuthorize("hasAnyRole('BUYER','SELLER','ADMIN')")
    public ResponseEntity<ApiResponse<List<OrderRefundInfo>>> getOrderRefunds(
            @PathVariable Long orderId,
            @AuthenticationPrincipal UserDetailsImpl user) {

        Long userId = user.getId();
        String role = user.getAuthorities().iterator().next().getAuthority();

        // Kiểm tra quyền truy cập
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Đơn hàng không tồn tại"));
        boolean isAdmin  = role.contains("ADMIN");
        boolean isBuyer  = order.getCustomerId().equals(userId);
        boolean isSeller = order.getSellerId().equals(userId);
        if (!isAdmin && !isBuyer && !isSeller) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền xem refunds của đơn hàng này");
        }

        // Kafka request-reply → payment-service
        Map<String, Object> request = new HashMap<>();
        request.put("order_id", orderId);
        Map<String, Object> response = kafkaReplyService.sendAndReceive(
                KafkaTopics.ORDER_REFUNDS_REQUEST, request);

        List<OrderRefundInfo> refunds = parseRefundList(response);
        return ResponseEntity.ok(ApiResponse.success(refunds));
    }

    // ─── GET /orders/{orderId}/refunds/{refundId} — Chi tiết 1 refund ─────────

    /**
     * Lấy chi tiết một refund cụ thể theo refundId.
     * Accessible by: Buyer (owner) | Admin
     */
    @GetMapping("/orders/{orderId}/refunds/{refundId}")
    @PreAuthorize("hasAnyRole('BUYER','ADMIN')")
    public ResponseEntity<ApiResponse<OrderRefundInfo>> getRefundDetail(
            @PathVariable Long orderId,
            @PathVariable Long refundId,
            @AuthenticationPrincipal UserDetailsImpl user) {

        Long userId = user.getId();
        String role = user.getAuthorities().iterator().next().getAuthority();

        // Kiểm tra quyền truy cập
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Đơn hàng không tồn tại"));
        boolean isAdmin = role.contains("ADMIN");
        boolean isBuyer = order.getCustomerId().equals(userId);
        if (!isAdmin && !isBuyer) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền xem refund này");
        }

        // Kafka request-reply → payment-service
        Map<String, Object> request = new HashMap<>();
        request.put("order_id", orderId);
        request.put("refund_id", refundId);
        Map<String, Object> response = kafkaReplyService.sendAndReceive(
                KafkaTopics.ORDER_REFUNDS_REQUEST, request);

        if (Boolean.TRUE.equals(response.get("error"))) {
            throw new AppException(ErrorCode.NOT_FOUND, "Refund không tồn tại");
        }

        List<OrderRefundInfo> refunds = parseRefundList(response);
        if (refunds.isEmpty()) {
            throw new AppException(ErrorCode.NOT_FOUND, "Refund không tồn tại");
        }

        return ResponseEntity.ok(ApiResponse.success(refunds.get(0)));
    }

    // ─── GET /orders/{orderId}/refunds/presigned-url ─────────────────────────

    /**
     * Lấy presigned URL để upload ảnh bằng chứng cho refund.
     * Yêu cầu: BUYER
     */
    @GetMapping("/orders/{orderId}/refunds/presigned-url")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ApiResponse<PresignedUrlResponse>> getRefundPresignedUrl(
            @PathVariable Long orderId,
            @RequestParam("file_name") String fileName,
            @RequestParam("content_type") String contentType,
            @AuthenticationPrincipal UserDetailsImpl user) {

        Long userId = user.getId();

        // Kiểm tra quyền truy cập
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Đơn hàng không tồn tại"));
        if (!order.getCustomerId().equals(userId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền truy cập đơn hàng này");
        }

        // Kafka request-reply → payment-service / storage-service
        Map<String, Object> request = new HashMap<>();
        request.put("order_id", orderId);
        request.put("user_id", userId);
        request.put("file_name", fileName);
        request.put("content_type", contentType);
        Map<String, Object> response = kafkaReplyService.sendAndReceive(
                KafkaTopics.ORDER_REFUND_PRESIGNED_URL_REQUEST, request);

        if (Boolean.TRUE.equals(response.get("error"))) {
            throw new AppException(ErrorCode.NOT_FOUND,
                    "Không thể tạo presigned URL: " + response.get("message"));
        }

        PresignedUrlResponse result = objectMapper.convertValue(response, PresignedUrlResponse.class);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ─── GET /orders/refunds — Tất cả refunds của Buyer ─────────────────────

    @GetMapping("/orders/refunds")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ApiResponse<List<OrderRefundInfo>>> getBuyerRefunds(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(name = "from_date", required = false) String fromDate,
            @RequestParam(name = "to_date",   required = false) String toDate,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetailsImpl user) {

        Map<String, Object> request = new HashMap<>();
        request.put("user_id",   user.getId());
        if (status   != null) request.put("status",    status);
        if (type     != null) request.put("type",      type);
        if (fromDate != null) request.put("from_date", fromDate);
        if (toDate   != null) request.put("to_date",   toDate);
        request.put("page", page);
        request.put("size", size);

        Map<String, Object> response = kafkaReplyService.sendAndReceive(
                KafkaTopics.ORDER_REFUNDS_REQUEST, request);

        List<OrderRefundInfo> refunds = parseRefundList(response);
        return ResponseEntity.ok(ApiResponse.success(refunds));
    }

    // ─── GET /orders/parent/{parentOrderId}/refund ───────────────────────────

    /**
     * Trạng thái Full Refund của đơn cha.
     */
    @GetMapping("/orders/parent/{parentOrderId}/refund")
    @PreAuthorize("hasAnyRole('BUYER','ADMIN')")
    public ResponseEntity<ApiResponse<FullRefundCreatedResponse>> getFullRefundStatus(
            @PathVariable Long parentOrderId,
            @AuthenticationPrincipal UserDetailsImpl user) {

        Long userId = user.getId();
        String role = user.getAuthorities().iterator().next().getAuthority();

        if (!role.contains("ADMIN")) {
            parentOrderRepository.findByIdAndCustomerId(parentOrderId, userId)
                    .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN,
                            "Đơn cha không tồn tại hoặc không thuộc về bạn"));
        }

        // Lấy tất cả sub-orders và query refunds của từng sub-order qua Kafka
        List<Order> subOrders = orderRepository.findAllByParentOrderId(parentOrderId);
        List<FullRefundCreatedResponse.SubRefundInfo> subInfos = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        String groupRef = null;
        String overallStatus = "PENDING";

        for (Order o : subOrders) {
            Map<String, Object> req = new HashMap<>();
            req.put("order_id", o.getId());
            Map<String, Object> response = kafkaReplyService.sendAndReceive(
                    KafkaTopics.ORDER_REFUNDS_REQUEST, req);
            List<OrderRefundInfo> refunds = parseRefundList(response);
            if (!refunds.isEmpty()) {
                OrderRefundInfo r = refunds.get(0);
                if (groupRef == null) groupRef = r.getGroupRef();
                BigDecimal amt = r.getAdjustAmount() != null ? r.getAdjustAmount() : r.getAmount();
                if (amt != null) totalAmount = totalAmount.add(amt);
                subInfos.add(FullRefundCreatedResponse.SubRefundInfo.builder()
                        .orderId(o.getId())
                        .sellerId(o.getSellerId())
                        .amount(amt)
                        .itemCount(0)
                        .build());
                if ("FAILED".equals(r.getStatus()) || "REJECTED".equals(r.getStatus())) {
                    overallStatus = r.getStatus();
                } else if ("PENDING".equals(r.getStatus()) && !"FAILED".equals(overallStatus)) {
                    overallStatus = "PENDING";
                } else if ("SUCCESS".equals(r.getStatus()) && "PENDING".equals(overallStatus)
                        && subInfos.size() == subOrders.size()) {
                    overallStatus = "SUCCESS";
                }
            }
        }

        return ResponseEntity.ok(ApiResponse.success(
                FullRefundCreatedResponse.builder()
                        .groupRef(groupRef)
                        .parentOrderId(parentOrderId)
                        .type("FULL")
                        .totalAmount(totalAmount)
                        .status(overallStatus)
                        .refunds(subInfos)
                        .build()
        ));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Kiểm tra order status hợp lệ để tạo refund.
     * Theo chính sách: chỉ cho phép khi đơn đã DELIVERED (trong vòng 7 ngày)
     * hoặc PARTIALLY_REFUNDED (đã hoàn một phần, vẫn trong cửa sổ 7 ngày).
     */
    private void validateOrderStatusForRefund(Order order) {
        String status = order.getStatus();
        if (!"DELIVERED".equals(status) && !"PARTIALLY_REFUNDED".equals(status)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Chỉ có thể yêu cầu hoàn tiền khi đơn hàng đã được giao (DELIVERED). "
                            + "Đơn " + order.getOrderCode() + " đang ở trạng thái " + status);
        }
        if (order.getUpdatedAt() == null
                || Duration.between(order.getUpdatedAt(), LocalDateTime.now()).toDays() > 7) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Đã quá 7 ngày kể từ khi nhận hàng, không thể yêu cầu hoàn tiền");
        }
    }

    @SuppressWarnings("unchecked")
    private List<OrderRefundInfo> parseRefundList(Map<String, Object> response) {
        if (Boolean.TRUE.equals(response.get("error"))) return List.of();
        Object raw = response.get("refunds");
        if (raw == null) return List.of();
        return objectMapper.convertValue(raw, new TypeReference<List<OrderRefundInfo>>() {});
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize Kafka payload: {}", e.getMessage());
            return "{}";
        }
    }
}
