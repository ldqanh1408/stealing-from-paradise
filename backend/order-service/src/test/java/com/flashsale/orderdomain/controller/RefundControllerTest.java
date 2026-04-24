package com.flashsale.orderdomain.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.orderdomain.domain.model.Order;
import com.flashsale.orderdomain.domain.model.OrderItem;
import com.flashsale.orderdomain.domain.model.ParentOrder;
import com.flashsale.orderdomain.domain.repository.OrderItemRepository;
import com.flashsale.orderdomain.domain.repository.OrderRepository;
import com.flashsale.orderdomain.domain.repository.ParentOrderRepository;
import com.flashsale.orderdomain.dto.request.BuyerPartialRefundItem;
import com.flashsale.orderdomain.dto.request.BuyerPartialRefundRequest;
import com.flashsale.orderdomain.dto.request.FullRefundRequest;
import com.flashsale.orderdomain.dto.response.FullRefundCreatedResponse;
import com.flashsale.orderdomain.dto.response.OrderRefundInfo;
import com.flashsale.orderdomain.dto.response.RefundCreatedResponse;
import com.flashsale.orderdomain.service.KafkaReplyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.extension.ExtendWith(MockitoExtension.class)
class RefundControllerTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private ParentOrderRepository parentOrderRepository;
    @Mock
    private KafkaReplyService kafkaReplyService;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private RefundController controller;

    @BeforeEach
    void setUp() {
        controller = new RefundController(
                orderRepository,
                orderItemRepository,
                parentOrderRepository,
                kafkaReplyService,
                kafkaTemplate,
                new ObjectMapper()
        );
    }

    @Test
    void createPartialRefund_returnsCreatedAndPublishesEvent() {
        Long orderId = 10L;
        UserDetailsImpl user = user(1L, "BUYER");
        BuyerPartialRefundRequest req = new BuyerPartialRefundRequest();
        req.setReason("Damaged");
        BuyerPartialRefundItem item = new BuyerPartialRefundItem();
        item.setOrderItemId(100L);
        item.setQuantity(1);
        req.setItems(List.of(item));
        req.setEvidenceImages(List.of("img1"));

        when(orderRepository.findByIdAndUserId(orderId, 1L)).thenReturn(Optional.of(deliveredOrder(orderId, 1L)));
        when(orderItemRepository.findAllByOrderId(orderId)).thenReturn(List.of(orderItem(100L, 2, 0, 15000)));

        ResponseEntity<com.flashsale.commonlib.dto.ApiResponse<RefundCreatedResponse>> result =
                controller.createPartialRefund(orderId, req, user);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertEquals("PARTIAL", result.getBody().getData().getType());
        verify(kafkaTemplate).send(eq(KafkaTopics.REFUND_REQUESTED), eq(String.valueOf(orderId)), anyString());
    }

    @Test
    void createFullRefund_returnsCreatedAndPublishesEvent() {
        Long parentOrderId = 20L;
        UserDetailsImpl user = user(2L, "BUYER");
        FullRefundRequest req = new FullRefundRequest();
        req.setReason("Product issue");
        req.setEvidenceImages(List.of("a", "b"));

        when(parentOrderRepository.findByIdAndUserId(parentOrderId, 2L))
                .thenReturn(Optional.of(ParentOrder.builder().id(parentOrderId).userId(2L).build()));
        when(orderRepository.findAllByParentOrderId(parentOrderId))
                .thenReturn(List.of(deliveredOrder(201L, 2L)));
        when(orderItemRepository.findAllByOrderId(201L))
                .thenReturn(List.of(orderItem(300L, 1, 0, 10000), orderItem(301L, 1, 0, 20000)));

        ResponseEntity<com.flashsale.commonlib.dto.ApiResponse<FullRefundCreatedResponse>> result =
                controller.createFullRefund(parentOrderId, req, user);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertEquals("FULL", result.getBody().getData().getType());
        verify(kafkaTemplate).send(eq(KafkaTopics.REFUND_FULL_REQUESTED), eq(String.valueOf(parentOrderId)), anyString());
    }

    @Test
    void createMultiSellerPartialRefund_returnsCreatedAndPublishesEvents() {
        Long parentOrderId = 30L;
        UserDetailsImpl user = user(3L, "BUYER");
        BuyerPartialRefundRequest req = new BuyerPartialRefundRequest();
        req.setReason("Need partial refund");
        BuyerPartialRefundItem item = new BuyerPartialRefundItem();
        item.setOrderItemId(400L);
        item.setQuantity(1);
        req.setItems(List.of(item));

        Order subOrder = deliveredOrder(301L, 3L);
        subOrder.setParentOrderId(parentOrderId);
        subOrder.setSellerId(999L);

        when(parentOrderRepository.findByIdAndUserId(parentOrderId, 3L))
                .thenReturn(Optional.of(ParentOrder.builder().id(parentOrderId).userId(3L).build()));
        when(orderRepository.findAllByParentOrderId(parentOrderId))
                .thenReturn(List.of(subOrder));
        when(orderItemRepository.findAllByOrderId(301L))
                .thenReturn(List.of(orderItem(400L, 2, 0, 12000)));

        ResponseEntity<com.flashsale.commonlib.dto.ApiResponse<FullRefundCreatedResponse>> result =
                controller.createMultiSellerPartialRefund(parentOrderId, req, user);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertEquals("PARTIAL", result.getBody().getData().getType());
        verify(kafkaTemplate).send(eq(KafkaTopics.REFUND_REQUESTED), eq("301"), anyString());
    }

    @Test
    void getOrderRefunds_returnsRefundList() {
        Long orderId = 40L;
        UserDetailsImpl user = user(4L, "BUYER");
        Order order = deliveredOrder(orderId, 4L);
        order.setSellerId(999L);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(kafkaReplyService.sendAndReceive(eq(KafkaTopics.ORDER_REFUNDS_REQUEST), anyMap()))
                .thenReturn(Map.of(
                        "refunds", List.of(Map.of(
                                "refund_id", 1L,
                                "order_id", orderId,
                                "type", "PARTIAL",
                                "status", "PENDING",
                                "amount", 1000
                        ))
                ));

        ResponseEntity<com.flashsale.commonlib.dto.ApiResponse<List<OrderRefundInfo>>> result =
                controller.getOrderRefunds(orderId, user);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(1, result.getBody().getData().size());
        assertEquals(orderId, result.getBody().getData().get(0).getOrderId());
    }

    @Test
    void getBuyerRefunds_returnsRefundList() {
        UserDetailsImpl user = user(5L, "BUYER");
        when(kafkaReplyService.sendAndReceive(eq(KafkaTopics.ORDER_REFUNDS_REQUEST), anyMap()))
                .thenReturn(Map.of(
                        "refunds", List.of(Map.of(
                                "refund_id", 2L,
                                "order_id", 50L,
                                "type", "FULL",
                                "status", "SUCCESS",
                                "amount", 5000
                        ))
                ));

        ResponseEntity<com.flashsale.commonlib.dto.ApiResponse<List<OrderRefundInfo>>> result =
                controller.getBuyerRefunds("SUCCESS", "FULL", "2026-01-01", "2026-01-31", 0, 20, user);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(1, result.getBody().getData().size());
        assertEquals("SUCCESS", result.getBody().getData().get(0).getStatus());
    }

    @Test
    void createPartialRefund_throwsWhenRequestedQuantityExceedsAvailable() {
        Long orderId = 70L;
        UserDetailsImpl user = user(7L, "BUYER");
        BuyerPartialRefundRequest req = new BuyerPartialRefundRequest();
        req.setReason("Too many");
        BuyerPartialRefundItem item = new BuyerPartialRefundItem();
        item.setOrderItemId(700L);
        item.setQuantity(3);
        req.setItems(List.of(item));

        when(orderRepository.findByIdAndUserId(orderId, 7L)).thenReturn(Optional.of(deliveredOrder(orderId, 7L)));
        when(orderItemRepository.findAllByOrderId(orderId)).thenReturn(List.of(orderItem(700L, 2, 0, 10000)));

        AppException ex = assertThrows(AppException.class, () -> controller.createPartialRefund(orderId, req, user));
        assertTrue(ex.getMessage().contains("vượt quá"));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void getOrderRefunds_throwsForbiddenForNonOwnerBuyer() {
        Long orderId = 80L;
        UserDetailsImpl user = user(8L, "BUYER");
        Order order = deliveredOrder(orderId, 99L);
        order.setSellerId(199L);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        AppException ex = assertThrows(AppException.class, () -> controller.getOrderRefunds(orderId, user));

        assertTrue(ex.getMessage().contains("không có quyền"));
        verifyNoInteractions(kafkaReplyService);
    }

    @Test
    void getBuyerRefunds_returnsEmptyListWhenKafkaReturnsError() {
        UserDetailsImpl user = user(9L, "BUYER");
        when(kafkaReplyService.sendAndReceive(eq(KafkaTopics.ORDER_REFUNDS_REQUEST), anyMap()))
                .thenReturn(Map.of("error", true));

        ResponseEntity<com.flashsale.commonlib.dto.ApiResponse<List<OrderRefundInfo>>> result =
                controller.getBuyerRefunds(null, null, null, null, 0, 20, user);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertTrue(result.getBody().getData().isEmpty());
    }

    @Test
    void getFullRefundStatus_returnsAggregatedResponse() {
        Long parentOrderId = 60L;
        UserDetailsImpl user = user(6L, "BUYER");
        Order order = deliveredOrder(601L, 6L);
        order.setParentOrderId(parentOrderId);
        order.setSellerId(123L);

        when(parentOrderRepository.findByIdAndUserId(parentOrderId, 6L))
                .thenReturn(Optional.of(ParentOrder.builder().id(parentOrderId).userId(6L).build()));
        when(orderRepository.findAllByParentOrderId(parentOrderId)).thenReturn(List.of(order));
        when(kafkaReplyService.sendAndReceive(eq(KafkaTopics.ORDER_REFUNDS_REQUEST), anyMap()))
                .thenReturn(Map.of(
                        "refunds", List.of(Map.of(
                                "group_ref", "grp-1",
                                "status", "SUCCESS",
                                "amount", 10000
                        ))
                ));

        ResponseEntity<com.flashsale.commonlib.dto.ApiResponse<FullRefundCreatedResponse>> result =
                controller.getFullRefundStatus(parentOrderId, user);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(parentOrderId, result.getBody().getData().getParentOrderId());
        assertEquals("SUCCESS", result.getBody().getData().getStatus());
    }

    @Test
    void getFullRefundStatus_returnsFailedWhenAnySubRefundFails() {
        Long parentOrderId = 61L;
        UserDetailsImpl user = user(61L, "BUYER");
        Order first = deliveredOrder(611L, 61L);
        first.setParentOrderId(parentOrderId);
        first.setSellerId(11L);
        Order second = deliveredOrder(612L, 61L);
        second.setParentOrderId(parentOrderId);
        second.setSellerId(12L);

        when(parentOrderRepository.findByIdAndUserId(parentOrderId, 61L))
                .thenReturn(Optional.of(ParentOrder.builder().id(parentOrderId).userId(61L).build()));
        when(orderRepository.findAllByParentOrderId(parentOrderId)).thenReturn(List.of(first, second));
        when(kafkaReplyService.sendAndReceive(eq(KafkaTopics.ORDER_REFUNDS_REQUEST), anyMap()))
                .thenReturn(Map.of(
                        "refunds", List.of(Map.of(
                                "group_ref", "grp-2",
                                "status", "SUCCESS",
                                "amount", 10000
                        ))
                ))
                .thenReturn(Map.of(
                        "refunds", List.of(Map.of(
                                "group_ref", "grp-2",
                                "status", "FAILED",
                                "amount", 5000
                        ))
                ));

        ResponseEntity<com.flashsale.commonlib.dto.ApiResponse<FullRefundCreatedResponse>> result =
                controller.getFullRefundStatus(parentOrderId, user);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("FAILED", result.getBody().getData().getStatus());
        assertEquals(2, result.getBody().getData().getRefunds().size());
    }

    private Order deliveredOrder(Long orderId, Long userId) {
        return Order.builder()
                .id(orderId)
                .parentOrderId(1L)
                .sellerId(10L)
                .sellerName("Seller")
                .orderCode("OR-" + orderId)
                .userId(userId)
                .totalAmt(BigDecimal.valueOf(20000))
                .finalAmt(BigDecimal.valueOf(20000))
                .status("DELIVERED")
                .deliveredAt(LocalDateTime.now().minusDays(1))
                .createdAt(LocalDateTime.now().minusDays(2))
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private OrderItem orderItem(Long id, int quantity, int refundedQuantity, int price) {
        return OrderItem.builder()
                .id(id)
                .quantity(quantity)
                .refundedQuantity(refundedQuantity)
                .priceSnapshot(BigDecimal.valueOf(price))
                .build();
    }

    private UserDetailsImpl user(Long id, String role) {
        return UserDetailsImpl.builder()
                .id(id)
                .username("u" + id)
                .email("u" + id + "@test.local")
                .password("p")
                .role(role)
                .enabled(true)
                .build();
    }
}
