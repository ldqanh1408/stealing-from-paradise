package com.flashsale.orderdomain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.orderdomain.axon.event.OrderCancelledEvent;
import com.flashsale.orderdomain.axon.event.OrderDeliveredEvent;
import com.flashsale.orderdomain.axon.event.OrderReturnedEvent;
import com.flashsale.orderdomain.domain.model.Order;
import com.flashsale.orderdomain.domain.repository.OrderItemRepository;
import com.flashsale.orderdomain.domain.repository.OrderRepository;
import com.flashsale.orderdomain.domain.repository.ParentOrderRepository;
import com.flashsale.orderdomain.dto.request.CancelOrderRequest;
import com.flashsale.orderdomain.dto.request.CheckoutRequest;
import com.flashsale.orderdomain.dto.request.ReturnToSenderRequest;
import com.flashsale.orderdomain.dto.response.CancelOrderResponse;
import com.flashsale.orderdomain.dto.response.ConfirmReceivedResponse;
import com.flashsale.orderdomain.dto.response.ReturnToSenderResponse;
import org.axonframework.eventhandling.gateway.EventGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ParentOrderRepository parentOrderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private KafkaReplyService kafkaReplyService;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    @Mock
    private EventGateway eventGateway;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository,
                parentOrderRepository,
                orderItemRepository,
                kafkaReplyService,
                kafkaTemplate,
                new ObjectMapper(),
                eventGateway
        );
    }

    @Test
    void checkout_throwsValidationFailedWhenCartItemsEmpty() {
        CheckoutRequest req = new CheckoutRequest();
        req.setAddressId(10L);
        req.setItemIds(List.of("item-1"));

        when(kafkaReplyService.sendAndReceive(eq(KafkaTopics.ORDER_ADDRESS_REQUEST), anyMap()))
                .thenReturn(Map.of(
                        "address_id", 10L,
                        "user_id", 1L,
                        "full_address", "A",
                        "province_id", 1,
                        "district_id", 2
                ));
        when(kafkaReplyService.sendAndReceive(eq(KafkaTopics.ORDER_CART_ITEMS_REQUEST), anyMap()))
                .thenReturn(Map.of("items", List.of()));

        AppException ex = assertThrows(AppException.class, () -> orderService.checkout(1L, req));

        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
        verify(parentOrderRepository, never()).save(any());
    }

    @Test
    void checkout_throwsValidationFailedWhenLoyaltyDiscountExceedsLimit() {
        CheckoutRequest req = new CheckoutRequest();
        req.setAddressId(10L);
        req.setItemIds(List.of("item-1"));
        req.setUseLoyaltyPoints(true);
        req.setLoyaltyPointsToUse(3); // 3000 > 20% of 10000

        when(kafkaReplyService.sendAndReceive(eq(KafkaTopics.ORDER_ADDRESS_REQUEST), anyMap()))
                .thenReturn(Map.of(
                        "address_id", 10L,
                        "user_id", 1L,
                        "full_address", "A",
                        "province_id", 1,
                        "district_id", 2
                ));
        when(kafkaReplyService.sendAndReceive(eq(KafkaTopics.ORDER_CART_ITEMS_REQUEST), anyMap()))
                .thenReturn(Map.of(
                        "items", List.of(Map.of(
                                "cart_item_id", "item-1",
                                "seller_id", 9L,
                                "seller_name", "Seller",
                                "price_snapshot", 10000,
                                "quantity", 1
                        ))
                ));

        AppException ex = assertThrows(AppException.class, () -> orderService.checkout(1L, req));

        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
        verify(parentOrderRepository, never()).save(any());
    }

    @Test
    void cancelOrder_throwsForbiddenWhenUserIsNotOwner() {
        Order order = baseOrder("PENDING");
        order.setUserId(100L);
        order.setSellerId(200L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        CancelOrderRequest req = new CancelOrderRequest();
        req.setReason("Changed mind");

        AppException ex = assertThrows(AppException.class,
                () -> orderService.cancelOrder(1L, 999L, "BUYER", req));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void cancelOrder_updatesOrderAndPublishesEvent() {
        Order order = baseOrder("PENDING");
        order.setUserId(100L);
        order.setSellerId(200L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        CancelOrderRequest req = new CancelOrderRequest();
        req.setReason("No longer needed");
        req.setNote("ordered by mistake");

        CancelOrderResponse response = orderService.cancelOrder(1L, 100L, "BUYER", req);

        assertEquals("CANCELLED", order.getStatus());
        assertEquals("BUYER", order.getCancelledBy());
        assertTrue(order.getCancelReason().contains("No longer needed"));
        assertNotNull(order.getCancelledAt());
        assertEquals("CANCELLED", response.getStatus());

        ArgumentCaptor<OrderCancelledEvent> captor = ArgumentCaptor.forClass(OrderCancelledEvent.class);
        verify(eventGateway).publish(captor.capture());
        assertEquals(order.getId(), captor.getValue().getOrderId());
        assertEquals("BUYER", captor.getValue().getCancelledBy());
    }

    @Test
    void confirmReceived_throwsWhenOrderNotInShippingStatus() {
        Order order = baseOrder("PAID");
        when(orderRepository.findByIdAndUserId(1L, 100L)).thenReturn(Optional.of(order));

        AppException ex = assertThrows(AppException.class,
                () -> orderService.confirmReceived(1L, 100L));

        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
    }

    @Test
    void confirmReceived_marksDeliveredAndPublishesEventWithLoyaltyPoints() {
        Order order = baseOrder("SHIPPING");
        order.setUserId(100L);
        order.setSellerId(200L);
        order.setFinalAmt(BigDecimal.valueOf(25_500)); // 2 points
        when(orderRepository.findByIdAndUserId(1L, 100L)).thenReturn(Optional.of(order));

        ConfirmReceivedResponse response = orderService.confirmReceived(1L, 100L);

        assertEquals("DELIVERED", order.getStatus());
        assertNotNull(order.getDeliveredAt());
        assertEquals(2, response.getLoyaltyPointsConfirmed());

        ArgumentCaptor<OrderDeliveredEvent> captor = ArgumentCaptor.forClass(OrderDeliveredEvent.class);
        verify(eventGateway).publish(captor.capture());
        assertEquals(2, captor.getValue().getLoyaltyPoints());
        assertEquals("BUYER", captor.getValue().getDeliveredBy());
    }

    @Test
    void returnToSender_throwsWhenEvidenceImagesMissing() {
        Order order = baseOrder("SHIPPING");
        when(orderRepository.findByIdAndSellerId(1L, 200L)).thenReturn(Optional.of(order));

        ReturnToSenderRequest req = new ReturnToSenderRequest();
        req.setReturnTrackingNumber("RTS-1");
        req.setEvidenceImages(List.of());

        AppException ex = assertThrows(AppException.class,
                () -> orderService.returnToSender(1L, 200L, req));

        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
    }

    @Test
    void returnToSender_throwsWhenEvidenceImagesExceedLimit() {
        Order order = baseOrder("SHIPPING");
        when(orderRepository.findByIdAndSellerId(1L, 200L)).thenReturn(Optional.of(order));

        ReturnToSenderRequest req = new ReturnToSenderRequest();
        req.setReturnTrackingNumber("RTS-2");
        req.setEvidenceImages(List.of(
                mock(MultipartFile.class), mock(MultipartFile.class), mock(MultipartFile.class),
                mock(MultipartFile.class), mock(MultipartFile.class), mock(MultipartFile.class)
        ));

        AppException ex = assertThrows(AppException.class,
                () -> orderService.returnToSender(1L, 200L, req));

        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
    }

    @Test
    void returnToSender_updatesOrderAndPublishesEvent() {
        Order order = baseOrder("SHIPPING");
        when(orderRepository.findByIdAndSellerId(1L, 200L)).thenReturn(Optional.of(order));

        ReturnToSenderRequest req = new ReturnToSenderRequest();
        req.setReturnTrackingNumber("RTS-3");
        req.setEvidenceImages(List.of(mock(MultipartFile.class), mock(MultipartFile.class)));

        ReturnToSenderResponse response = orderService.returnToSender(1L, 200L, req);

        assertEquals("RETURNED", order.getStatus());
        assertEquals("RTS-3", order.getReturnTrackingNumber());
        assertEquals("RETURNED", response.getOrderStatus());
        assertEquals(2, response.getEvidenceCount());

        ArgumentCaptor<OrderReturnedEvent> captor = ArgumentCaptor.forClass(OrderReturnedEvent.class);
        verify(eventGateway).publish(captor.capture());
        assertEquals(order.getId(), captor.getValue().getOrderId());
        assertEquals(2, captor.getValue().getEvidenceCount());
        assertEquals("RTS-3", captor.getValue().getReturnTrackingNumber());
    }

    private Order baseOrder(String status) {
        return Order.builder()
                .id(1L)
                .parentOrderId(10L)
                .orderCode("OR-20260101-1")
                .userId(100L)
                .sellerId(200L)
                .sellerName("Seller A")
                .totalAmt(BigDecimal.valueOf(50_000))
                .finalAmt(BigDecimal.valueOf(50_000))
                .status(status)
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
