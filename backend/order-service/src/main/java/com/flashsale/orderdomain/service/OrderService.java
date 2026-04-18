package com.flashsale.orderdomain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.dto.PageResponse;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.flashsale.orderdomain.axon.event.*;
import com.flashsale.orderdomain.client.dto.AddressInfo;
import com.flashsale.orderdomain.client.dto.CartItemInfo;
import com.flashsale.orderdomain.domain.model.Order;
import com.flashsale.orderdomain.domain.model.OrderItem;
import com.flashsale.orderdomain.domain.model.ParentOrder;
import com.flashsale.orderdomain.domain.repository.OrderItemRepository;
import com.flashsale.orderdomain.domain.repository.OrderRepository;
import com.flashsale.orderdomain.domain.repository.ParentOrderRepository;
import com.flashsale.orderdomain.dto.request.CancelOrderRequest;
import com.flashsale.orderdomain.dto.request.CheckoutRequest;
import com.flashsale.orderdomain.dto.request.ReturnToSenderRequest;
import com.flashsale.orderdomain.dto.request.UpdateTrackingRequest;
import com.flashsale.orderdomain.dto.response.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final ParentOrderRepository parentOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final KafkaReplyService kafkaReplyService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final EventGateway eventGateway;

    // Thời gian timeout thanh toán: 30 phút
    private static final int PAYMENT_TIMEOUT_MINUTES = 30;
    // Thời hạn giao hàng mặc định: 3 ngày
    private static final int DEFAULT_SHIPPING_DAYS = 3;
    // Điểm loyalty xác nhận khi nhận hàng (giả định)
    private static final int LOYALTY_POINTS_PER_10K = 1; // 1 điểm / 10.000 VND

    // ─── Checkout ──────────────────────────────────────────────────────────────

    @Transactional
    public CheckoutResponse checkout(Long userId, CheckoutRequest req) {
        // 1. Validate địa chỉ giao hàng (Kafka request-reply → identity-service)
        AddressInfo address = fetchAddress(req.getAddressId(), userId);

        // 2. Lấy cart items từ Cart Service (Kafka request-reply → cart-service)
        List<CartItemInfo> cartItems = fetchCartItems(userId, req.getItemIds());
        if (cartItems.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Không có item hợp lệ trong giỏ hàng");
        }

        // 3. Tính tổng tiền
        BigDecimal totalAmt = cartItems.stream()
                .map(item -> item.getPriceSnapshot().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4. Validate loyalty points (tối đa 20% giá trị đơn)
        BigDecimal loyaltyDiscount = BigDecimal.ZERO;
        int loyaltyPointsUsed = 0;
        if (Boolean.TRUE.equals(req.getUseLoyaltyPoints()) && req.getLoyaltyPointsToUse() > 0) {
            BigDecimal maxDiscount = totalAmt.multiply(BigDecimal.valueOf(0.2));
            BigDecimal requestedDiscount = BigDecimal.valueOf(req.getLoyaltyPointsToUse() * 1000L); // 1 point = 1000 VND
            if (requestedDiscount.compareTo(maxDiscount) > 0) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Loyalty points vượt quá giới hạn 20% giá trị đơn");
            }
            loyaltyDiscount = requestedDiscount;
            loyaltyPointsUsed = req.getLoyaltyPointsToUse();
        }

        BigDecimal finalAmt = totalAmt.subtract(loyaltyDiscount);

        // 5. Group items by seller
        Map<Long, List<CartItemInfo>> itemsBySeller = cartItems.stream()
                .collect(Collectors.groupingBy(CartItemInfo::getSellerId));

        // 6. Build shipping address JSON
        String shippingAddressJson = buildShippingAddressJson(address);

        // 7. Tạo ParentOrder (orderCode được set sau khi có ID)
        ParentOrder parentOrder = ParentOrder.builder()
                .orderCode("PO-PENDING")   // placeholder, sẽ cập nhật sau khi save
                .userId(userId)
                .totalAmt(totalAmt)
                .loyaltyDiscount(loyaltyDiscount)
                .loyaltyPointsUsed(loyaltyPointsUsed)
                .finalAmt(finalAmt)
                .addressId(req.getAddressId())
                .timeoutAt(LocalDateTime.now().plusMinutes(PAYMENT_TIMEOUT_MINUTES))
                .build();
        parentOrder = parentOrderRepository.save(parentOrder);
        // Update orderCode với ID thực
        String parentOrderCode = "PO-"
                + parentOrder.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + parentOrder.getId();
        parentOrder.setOrderCode(parentOrderCode);
        parentOrder = parentOrderRepository.save(parentOrder);

        // 8. Tạo sub-orders theo từng seller
        List<Order> subOrders = new ArrayList<>();
        List<CheckoutSubOrderResponse> subOrderResponses = new ArrayList<>();

        for (Map.Entry<Long, List<CartItemInfo>> entry : itemsBySeller.entrySet()) {
            Long sellerId = entry.getKey();
            List<CartItemInfo> sellerItems = entry.getValue();
            String sellerName = sellerItems.get(0).getSellerName();

            BigDecimal sellerTotal = sellerItems.stream()
                    .map(i -> i.getPriceSnapshot().multiply(BigDecimal.valueOf(i.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Phân bổ discount theo tỷ lệ
            BigDecimal sellerDiscount = totalAmt.compareTo(BigDecimal.ZERO) > 0
                    ? loyaltyDiscount.multiply(sellerTotal).divide(totalAmt, 2, java.math.RoundingMode.DOWN)
                    : BigDecimal.ZERO;

            Order order = Order.builder()
                    .parentOrderId(parentOrder.getId())
                    .sellerId(sellerId)
                    .sellerName(sellerName)
                    .orderCode("OR-PENDING")  // placeholder, cập nhật sau khi có ID
                    .userId(userId)
                    .totalAmt(sellerTotal)
                    .finalAmt(sellerTotal.subtract(sellerDiscount))
                    .status("PENDING")
                    .isFlashSale(sellerItems.stream().anyMatch(i -> i.getFsItemId() != null))
                    .shippingAddress(shippingAddressJson)
                    .build();
            order = orderRepository.save(order);
            String orderCode = "OR-"
                    + order.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                    + "-" + order.getId();
            order.setOrderCode(orderCode);
            order = orderRepository.save(order);

            // Tạo order items
            List<CheckoutOrderItem> itemResponses = new ArrayList<>();
            for (CartItemInfo ci : sellerItems) {
                BigDecimal subtotal = ci.getPriceSnapshot().multiply(BigDecimal.valueOf(ci.getQuantity()));
                OrderItem orderItem = OrderItem.builder()
                        .order(order)
                        .productId(ci.getProductId())
                        .skuCode(ci.getSkuCode())
                        .variantId(ci.getVariantId())
                        .nameSnapshot(ci.getProductName())
                        .variantName(ci.getVariantName())
                        .imageSnapshot(ci.getImageUrl())
                        .priceSnapshot(ci.getPriceSnapshot())
                        .quantity(ci.getQuantity())
                        .refundedQuantity(0)
                        .fsItemId(ci.getFsItemId())
                        .build();
                orderItemRepository.save(orderItem);

                itemResponses.add(CheckoutOrderItem.builder()
                        .orderItemId(orderItem.getId())
                        .skuCode(ci.getSkuCode())
                        .productName(ci.getProductName())
                        .variantName(ci.getVariantName())
                        .imageSnapshot(ci.getImageUrl())
                        .priceSnapshot(ci.getPriceSnapshot())
                        .quantity(ci.getQuantity())
                        .subtotal(subtotal)
                        .build());
            }

            subOrders.add(order);
            subOrderResponses.add(CheckoutSubOrderResponse.builder()
                    .orderId(order.getId())
                    .orderCode(orderCode)
                    .sellerId(sellerId)
                    .sellerName(sellerName)
                    .totalAmt(sellerTotal)
                    .finalAmt(order.getFinalAmt())
                    .status("PENDING")
                    .items(itemResponses)
                    .createdAt(order.getCreatedAt().toInstant(ZoneOffset.UTC))
                    .build());
        }

        // 9. Emit one OrderCreatedEvent per sub-order → Saga starts, publishes order.created to Kafka
        final int finalLoyaltyPointsUsed = loyaltyPointsUsed;
        final ParentOrder finalParentOrder = parentOrder;
        subOrders.forEach(o -> eventGateway.publish(new OrderCreatedEvent(
                o.getId(),
                finalParentOrder.getId(),
                userId,
                o.getSellerId(),
                o.getSellerName(),
                o.getOrderCode(),
                o.getTotalAmt(),
                finalLoyaltyPointsUsed,
                Boolean.TRUE.equals(o.getIsFlashSale()),
                finalParentOrder.getTimeoutAt()
        )));

        // 10. Emit parent-order event so Axon saga can orchestrate payment flow once per checkout
        eventGateway.publish(new ParentOrderCheckoutCreatedEvent(
                parentOrder.getId(),
                userId,
                finalAmt,
                parentOrder.getTimeoutAt()
        ));

        // 11. Publish order.checkout_completed → Cart Service xóa items (no Saga needed)
        publishCheckoutCompleted(userId, req.getItemIds(), parentOrder.getId());

        log.info("Checkout completed: parentOrderId={}, userId={}, totalAmt={}", parentOrder.getId(), userId, totalAmt);

        return CheckoutResponse.builder()
                .parentOrderId(parentOrder.getId())
                .orderCode(parentOrderCode)
                .orders(subOrderResponses)
                .shippingAddress(CheckoutResponse.ShippingAddressInfo.builder()
                        .addressId(address.getAddressId())
                        .fullAddress(address.getFullAddress())
                        .provinceId(address.getProvinceId())
                        .districtId(address.getDistrictId())
                        .build())
                .payment(CheckoutResponse.PaymentSummary.builder()
                        .totalAmount(totalAmt)
                        .loyaltyDiscount(loyaltyDiscount)
                        .loyaltyPointsUsed(loyaltyPointsUsed)
                        .finalAmount(finalAmt)
                        .currency("VND")
                        .build())
                .totalItems(cartItems.stream().mapToInt(CartItemInfo::getQuantity).sum())
                .totalSellers(itemsBySeller.size())
                .paymentStatus("PENDING")
                .timeoutAt(parentOrder.getTimeoutAt().toInstant(ZoneOffset.UTC))
                .createdAt(parentOrder.getCreatedAt().toInstant(ZoneOffset.UTC))
                .build();
    }

    // ─── Get Orders (Buyer) ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> getBuyerOrders(
            Long userId, String status, LocalDateTime fromDate, LocalDateTime toDate,
            int page, int size) {

        Page<Order> orders = orderRepository.findByUserIdWithFilters(
                userId, status, fromDate, toDate, PageRequest.of(page, size));

        Page<OrderSummaryResponse> mapped = orders.map(OrderSummaryResponse::from);
        return PageResponse.of(mapped);
    }

    // ─── Get Order Detail ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderDetail(Long orderId, Long userId, String role) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Đơn hàng không tồn tại"));

        // Kiểm tra quyền truy cập: Buyer hoặc Seller chủ đơn
        boolean isBuyer  = order.getUserId().equals(userId);
        boolean isSeller = order.getSellerId().equals(userId);
        if (!isBuyer && !isSeller) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền xem đơn hàng này");
        }

        List<OrderItem> items = orderItemRepository.findAllByOrderId(orderId);

        // Parse shipping address JSON
        OrderDetailResponse.ShippingAddressInfo shippingAddr = parseShippingAddress(order.getShippingAddress());

        return OrderDetailResponse.builder()
                .orderId(order.getId())
                .parentOrderId(order.getParentOrderId())
                .orderCode(order.getOrderCode())
                .sellerId(order.getSellerId())
                .sellerName(order.getSellerName())
                .buyerId(order.getUserId())
                .status(order.getStatus())
                .totalAmt(order.getTotalAmt())
                .finalAmt(order.getFinalAmt())
                .isFlashSale(order.getIsFlashSale())
                .cancelledBy(order.getCancelledBy())
                .cancelReason(order.getCancelReason())
                .shippingAddress(shippingAddr)
                .trackingNumber(order.getTrackingNumber())
                .carrier(order.getCarrier())
                .shippingDeadline(order.getShippingDeadline() != null
                        ? order.getShippingDeadline().toInstant(ZoneOffset.UTC) : null)
                .returnTrackingNumber(order.getReturnTrackingNumber())
                .items(items.stream().map(OrderItemResponse::from).collect(Collectors.toList()))
                .createdAt(order.getCreatedAt().toInstant(ZoneOffset.UTC))
                .updatedAt(order.getUpdatedAt().toInstant(ZoneOffset.UTC))
                .build();
    }

    // ─── Get Parent Order Detail ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ParentOrderDetailResponse getParentOrderDetail(Long parentOrderId, Long userId) {
        ParentOrder parentOrder = parentOrderRepository.findByIdAndUserIdWithOrders(parentOrderId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN,
                        "Đơn cha không tồn tại hoặc không thuộc về bạn"));

        List<OrderSummaryResponse> subOrders = parentOrder.getOrders().stream()
                .map(OrderSummaryResponse::from)
                .collect(Collectors.toList());

        return ParentOrderDetailResponse.builder()
                .parentOrderId(parentOrder.getId())
                .orderCode(parentOrder.getOrderCode())
                .userId(parentOrder.getUserId())
                .totalAmt(parentOrder.getTotalAmt())
                .loyaltyDiscount(parentOrder.getLoyaltyDiscount())
                .loyaltyPointsUsed(parentOrder.getLoyaltyPointsUsed())
                .finalAmt(parentOrder.getFinalAmt())
                .addressId(parentOrder.getAddressId())
                .timeoutAt(parentOrder.getTimeoutAt() != null
                        ? parentOrder.getTimeoutAt().toInstant(ZoneOffset.UTC) : null)
                .orders(subOrders)
                .createdAt(parentOrder.getCreatedAt().toInstant(ZoneOffset.UTC))
                .updatedAt(parentOrder.getUpdatedAt().toInstant(ZoneOffset.UTC))
                .build();
    }

    // ─── Cancel Order ─────────────────────────────────────────────────────────

    @Transactional
    public CancelOrderResponse cancelOrder(Long orderId, Long userId, String role, CancelOrderRequest req) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Đơn hàng không tồn tại"));

        // Kiểm tra quyền: Buyer hoặc Seller chủ đơn
        boolean isBuyer  = order.getUserId().equals(userId);
        boolean isSeller = order.getSellerId().equals(userId);
        if (!isBuyer && !isSeller) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền hủy đơn hàng này");
        }

        // Chỉ hủy được khi đơn ở trạng thái PENDING
        if (!"PENDING".equals(order.getStatus())) {
            throw new AppException(ErrorCode.ORDER_NOT_CANCELLABLE,
                    "Chỉ có thể hủy đơn ở trạng thái PENDING");
        }

        String cancelledBy = isBuyer ? "BUYER" : "SELLER";
        String cancelReason = req.getNote() != null
                ? req.getReason() + " - " + req.getNote()
                : req.getReason();

        order.setStatus("CANCELLED");
        order.setCancelledBy(cancelledBy);
        order.setCancelReason(cancelReason);
        order.setCancelledAt(LocalDateTime.now());
        orderRepository.save(order);

        // Emit Axon event → Saga publishes order.cancelled (and seller.order_cancelled if needed)
        eventGateway.publish(new OrderCancelledEvent(
                order.getId(),
                order.getParentOrderId(),
                userId,
                order.getSellerId(),
                cancelledBy,
                cancelReason,
                order.getTotalAmt()
        ));

        log.info("Order cancelled: orderId={}, cancelledBy={}", orderId, cancelledBy);

        return CancelOrderResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .status("CANCELLED")
                .cancelledBy(cancelledBy)
                .cancelReason(cancelReason)
                .cancelledAt(order.getCancelledAt().toInstant(ZoneOffset.UTC))
                .build();
    }

    // ─── Update Tracking (Seller) ─────────────────────────────────────────────

    @Transactional
    public TrackingUpdateResponse updateTracking(Long orderId, Long sellerId, UpdateTrackingRequest req) {
        Order order = orderRepository.findByIdAndSellerId(orderId, sellerId)
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN,
                        "Đơn hàng không tồn tại hoặc không thuộc về bạn"));

        // Chỉ cập nhật tracking khi đơn ở trạng thái PAID
        if (!"PAID".equals(order.getStatus())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Chỉ có thể cập nhật tracking khi đơn ở trạng thái PAID");
        }

        LocalDateTime shippingDeadline = LocalDateTime.now().plusDays(DEFAULT_SHIPPING_DAYS);

        order.setTrackingNumber(req.getTrackingNumber());
        order.setCarrier(req.getCarrier());
        order.setStatus("SHIPPING");
        order.setShippingDeadline(shippingDeadline);
        orderRepository.save(order);

        // Emit Axon event → Saga publishes order.shipped and schedules shipping deadline
        eventGateway.publish(new OrderShippedEvent(
                order.getId(),
                order.getUserId(),
                order.getSellerId(),
                req.getTrackingNumber(),
                req.getCarrier(),
                shippingDeadline
        ));

        log.info("Order tracking updated: orderId={}, trackingNumber={}", orderId, req.getTrackingNumber());

        return TrackingUpdateResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .status("SHIPPING")
                .trackingNumber(req.getTrackingNumber())
                .carrier(req.getCarrier())
                .shippingDeadline(shippingDeadline.toInstant(ZoneOffset.UTC))
                .updatedAt(order.getUpdatedAt().toInstant(ZoneOffset.UTC))
                .build();
    }

    // ─── Confirm Received (Buyer) ─────────────────────────────────────────────

    @Transactional
    public ConfirmReceivedResponse confirmReceived(Long orderId, Long userId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN,
                        "Đơn hàng không tồn tại hoặc không thuộc về bạn"));

        if (!"SHIPPING".equals(order.getStatus())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Chỉ có thể xác nhận nhận hàng khi đơn đang ở trạng thái SHIPPING");
        }

        LocalDateTime now = LocalDateTime.now();
        order.setStatus("DELIVERED");
        order.setDeliveredAt(now);
        orderRepository.save(order);

        // Tính điểm loyalty xác nhận (1 điểm / 10.000 VND)
        int loyaltyPoints = order.getFinalAmt()
                .divide(BigDecimal.valueOf(10_000), 0, java.math.RoundingMode.DOWN)
                .multiply(BigDecimal.valueOf(LOYALTY_POINTS_PER_10K))
                .intValue();

        // Emit Axon event → Saga publishes order.delivered (ends saga)
        eventGateway.publish(new OrderDeliveredEvent(
                order.getId(),
                order.getUserId(),
                order.getSellerId(),
                order.getFinalAmt(),
                loyaltyPoints,
                "BUYER"
        ));

        log.info("Order delivered confirmed: orderId={}, loyaltyPoints={}", orderId, loyaltyPoints);

        return ConfirmReceivedResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .status("DELIVERED")
                .deliveredAt(now.toInstant(ZoneOffset.UTC))
                .loyaltyPointsConfirmed(loyaltyPoints)
                .sellerTrustScoreDelta(5)
                .build();
    }

    // ─── Return To Sender (Seller) ────────────────────────────────────────────

    @Transactional
    public ReturnToSenderResponse returnToSender(Long orderId, Long sellerId, ReturnToSenderRequest req) {
        Order order = orderRepository.findByIdAndSellerId(orderId, sellerId)
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN,
                        "Đơn hàng không tồn tại hoặc không thuộc về bạn"));

        // Chỉ cho phép khi đơn đang SHIPPING
        if (!"SHIPPING".equals(order.getStatus())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Chỉ có thể xác nhận hoàn hàng khi đơn đang ở trạng thái SHIPPING");
        }

        // Validate evidence images
        List<MultipartFile> images = req.getEvidenceImages();
        if (images == null || images.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Cần cung cấp ít nhất 1 ảnh bằng chứng");
        }
        if (images.size() > 5) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Tối đa 5 ảnh bằng chứng");
        }

        order.setStatus("RETURNED");
        order.setReturnTrackingNumber(req.getReturnTrackingNumber());
        orderRepository.save(order);

        // Tạo refund code (placeholder — sẽ được tạo bởi Refund Service sau khi nhận event)
        String refundCode = "RF-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + order.getId();

        // Emit Axon event → Saga publishes order.returned (ends saga)
        eventGateway.publish(new OrderReturnedEvent(
                order.getId(),
                order.getParentOrderId(),
                order.getUserId(),
                order.getSellerId(),
                order.getFinalAmt(),
                req.getReturnTrackingNumber(),
                images.size()
        ));

        log.info("Return-to-sender processed: orderId={}, evidenceCount={}", orderId, images.size());

        return ReturnToSenderResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .orderStatus("RETURNED")
                .refundCode(refundCode)
                .refundStatus("PENDING")
                .refundAmount(order.getFinalAmt())
                .returnTrackingNumber(req.getReturnTrackingNumber())
                .evidenceCount(images.size())
                .estimatedRefundDays(3)
                .message("Hàng hoàn đã được ghi nhận. Hệ thống đang tự động hoàn tiền cho Buyer.")
                .sellerNotification(ReturnToSenderResponse.NotificationInfo.builder()
                        .status("sent")
                        .message("Xác nhận hàng hoàn đã được lưu. Tồn kho đã được cộng lại.")
                        .build())
                .buyerNotification(ReturnToSenderResponse.NotificationInfo.builder()
                        .status("sent")
                        .message("Seller đã nhận lại hàng hoàn. Tiền đang được hoàn về tài khoản của bạn.")
                        .build())
                .createdAt(Instant.now())
                .build();
    }

    // ─── Get Seller Orders ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> getSellerOrders(
            Long sellerId, String status, LocalDateTime fromDate, LocalDateTime toDate,
            int page, int size) {

        Page<Order> orders = orderRepository.findBySellerIdWithFilters(
                sellerId, status, fromDate, toDate, PageRequest.of(page, size));

        Page<OrderSummaryResponse> mapped = orders.map(OrderSummaryResponse::from);
        return PageResponse.of(mapped);
    }


    // ─── Kafka Producers (internal only) ──────────────────────────────────────

    /** order.checkout_completed → cart-service removes purchased items. */
    private void publishCheckoutCompleted(Long userId, List<String> itemIds, Long parentOrderId) {
        Map<String, Object> event = Map.of(
                "user_id", userId,
                "item_ids", itemIds,
                "parent_order_id", parentOrderId,
                "timestamp", Instant.now().toString()
        );
        kafkaTemplate.send(KafkaTopics.ORDER_CHECKOUT_COMPLETED, String.valueOf(userId), toJson(event));
    }

    // ─── Kafka Request-Reply Helpers ──────────────────────────────────────────

    private AddressInfo fetchAddress(Long addressId, Long userId) {
        Map<String, Object> request = new HashMap<>();
        request.put("address_id", addressId);
        request.put("user_id", userId);

        Map<String, Object> response = kafkaReplyService.sendAndReceive(
                KafkaTopics.ORDER_ADDRESS_REQUEST, request);

        if (Boolean.TRUE.equals(response.get("error"))) {
            throw new AppException(ErrorCode.NOT_FOUND, "Địa chỉ không tồn tại hoặc không thuộc về bạn");
        }
        return objectMapper.convertValue(response, AddressInfo.class);
    }

    @SuppressWarnings("unchecked")
    private List<CartItemInfo> fetchCartItems(Long userId, List<String> itemIds) {
        Map<String, Object> request = new HashMap<>();
        request.put("user_id", userId);
        request.put("item_ids", itemIds);

        Map<String, Object> response = kafkaReplyService.sendAndReceive(
                KafkaTopics.ORDER_CART_ITEMS_REQUEST, request);

        if (Boolean.TRUE.equals(response.get("error"))) {
            throw new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy cart items");
        }
        Object items = response.get("items");
        return objectMapper.convertValue(items, new TypeReference<List<CartItemInfo>>() {});
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize Kafka payload: {}", e.getMessage());
            return "{}";
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String buildShippingAddressJson(AddressInfo address) {
        try {
            Map<String, Object> addr = Map.of(
                    "full_address", address.getFullAddress(),
                    "province_id", address.getProvinceId(),
                    "district_id", address.getDistrictId()
            );
            return objectMapper.writeValueAsString(addr);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize shipping address", e);
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private OrderDetailResponse.ShippingAddressInfo parseShippingAddress(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            return OrderDetailResponse.ShippingAddressInfo.builder()
                    .fullAddress((String) map.get("full_address"))
                    .provinceId(map.get("province_id") != null
                            ? Integer.parseInt(map.get("province_id").toString()) : null)
                    .districtId(map.get("district_id") != null
                            ? Integer.parseInt(map.get("district_id").toString()) : null)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse shipping address JSON", e);
            return null;
        }
    }
}
