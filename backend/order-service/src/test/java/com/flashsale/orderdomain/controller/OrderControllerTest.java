package com.flashsale.orderdomain.controller;

import com.flashsale.commonlib.dto.PageResponse;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.orderdomain.dto.request.CancelOrderRequest;
import com.flashsale.orderdomain.dto.request.CheckoutRequest;
import com.flashsale.orderdomain.dto.request.UpdateTrackingRequest;
import com.flashsale.orderdomain.dto.response.*;
import com.flashsale.orderdomain.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.extension.ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;

    private OrderController controller;

    @BeforeEach
    void setUp() {
        controller = new OrderController(orderService);
    }

    @Test
    void checkout_returnsCreated() {
        UserDetailsImpl user = user(1L, "BUYER");
        CheckoutRequest req = new CheckoutRequest();
        req.setAddressId(11L);
        req.setItemIds(List.of("i1"));
        CheckoutResponse response = CheckoutResponse.builder()
                .parentOrderId(100L)
                .orderCode("PO-1")
                .paymentStatus("PENDING")
                .build();

        when(orderService.checkout(1L, req)).thenReturn(response);

        ResponseEntity<com.flashsale.commonlib.dto.ApiResponse<CheckoutResponse>> result =
                controller.checkout(user, req);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertTrue(result.getBody().isSuccess());
        assertEquals(100L, result.getBody().getData().getParentOrderId());
    }

    @Test
    void getBuyerOrders_capsSizeTo100() {
        UserDetailsImpl user = user(2L, "BUYER");
        PageResponse<OrderSummaryResponse> pageResponse = PageResponse.<OrderSummaryResponse>builder()
                .content(List.of())
                .page(0)
                .size(100)
                .totalElements(0)
                .totalPages(0)
                .last(true)
                .build();
        when(orderService.getBuyerOrders(eq(2L), eq("PENDING"), any(), any(), eq(0), eq(100)))
                .thenReturn(pageResponse);

        ResponseEntity<com.flashsale.commonlib.dto.ApiResponse<PageResponse<OrderSummaryResponse>>> result =
                controller.getBuyerOrders(user, "PENDING", LocalDate.now().minusDays(1), LocalDate.now(), 0, 200);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertTrue(result.getBody().isSuccess());
        verify(orderService).getBuyerOrders(eq(2L), eq("PENDING"), any(LocalDateTime.class), any(LocalDateTime.class), eq(0), eq(100));
    }

    @Test
    void getOrderDetail_returnsData() {
        UserDetailsImpl user = user(3L, "SELLER");
        OrderDetailResponse response = OrderDetailResponse.builder()
                .orderId(44L)
                .status("PAID")
                .build();
        when(orderService.getOrderDetail(44L, 3L, "SELLER")).thenReturn(response);

        ResponseEntity<com.flashsale.commonlib.dto.ApiResponse<OrderDetailResponse>> result =
                controller.getOrderDetail(44L, user);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(44L, result.getBody().getData().getOrderId());
    }

    @Test
    void getParentOrderDetail_returnsData() {
        UserDetailsImpl user = user(4L, "BUYER");
        ParentOrderDetailResponse response = ParentOrderDetailResponse.builder()
                .parentOrderId(88L)
                .build();
        when(orderService.getParentOrderDetail(88L, 4L)).thenReturn(response);

        ResponseEntity<com.flashsale.commonlib.dto.ApiResponse<ParentOrderDetailResponse>> result =
                controller.getParentOrderDetail(88L, user);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(88L, result.getBody().getData().getParentOrderId());
    }

    @Test
    void cancelOrder_returnsData() {
        UserDetailsImpl user = user(5L, "BUYER");
        CancelOrderRequest req = new CancelOrderRequest();
        req.setReason("changed");
        CancelOrderResponse response = CancelOrderResponse.builder()
                .orderId(55L)
                .status("CANCELLED")
                .build();
        when(orderService.cancelOrder(55L, 5L, "BUYER", req)).thenReturn(response);

        ResponseEntity<com.flashsale.commonlib.dto.ApiResponse<CancelOrderResponse>> result =
                controller.cancelOrder(55L, user, req);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("CANCELLED", result.getBody().getData().getStatus());
    }

    @Test
    void updateTracking_returnsData() {
        UserDetailsImpl user = user(6L, "SELLER");
        UpdateTrackingRequest req = new UpdateTrackingRequest();
        req.setTrackingNumber("TN123");
        req.setCarrier("VNPOST");
        TrackingUpdateResponse response = TrackingUpdateResponse.builder()
                .orderId(66L)
                .status("SHIPPING")
                .build();
        when(orderService.updateTracking(66L, 6L, req)).thenReturn(response);

        ResponseEntity<com.flashsale.commonlib.dto.ApiResponse<TrackingUpdateResponse>> result =
                controller.updateTracking(66L, user, req);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("SHIPPING", result.getBody().getData().getStatus());
    }

    @Test
    void confirmReceived_returnsData() {
        UserDetailsImpl user = user(7L, "BUYER");
        ConfirmReceivedResponse response = ConfirmReceivedResponse.builder()
                .orderId(77L)
                .status("DELIVERED")
                .loyaltyPointsConfirmed(2)
                .build();
        when(orderService.confirmReceived(77L, 7L)).thenReturn(response);

        ResponseEntity<com.flashsale.commonlib.dto.ApiResponse<ConfirmReceivedResponse>> result =
                controller.confirmReceived(77L, user);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("DELIVERED", result.getBody().getData().getStatus());
        assertEquals(2, result.getBody().getData().getLoyaltyPointsConfirmed());
    }

    @Test
    void returnToSender_buildsRequestAndCallsService() {
        UserDetailsImpl user = user(8L, "SELLER");
        MultipartFile f1 = new MockMultipartFile("evidence_images", "a.jpg", "image/jpeg", "x".getBytes());
        MultipartFile f2 = new MockMultipartFile("evidence_images", "b.jpg", "image/jpeg", "y".getBytes());
        ReturnToSenderResponse response = ReturnToSenderResponse.builder()
                .orderId(88L)
                .orderStatus("RETURNED")
                .refundAmount(BigDecimal.valueOf(1000))
                .createdAt(Instant.now())
                .build();

        when(orderService.returnToSender(eq(88L), eq(8L), any())).thenReturn(response);

        ResponseEntity<com.flashsale.commonlib.dto.ApiResponse<ReturnToSenderResponse>> result =
                controller.returnToSender(88L, user, List.of(f1, f2), "RTN-1", "note");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("RETURNED", result.getBody().getData().getOrderStatus());

        ArgumentCaptor<com.flashsale.orderdomain.dto.request.ReturnToSenderRequest> captor =
                ArgumentCaptor.forClass(com.flashsale.orderdomain.dto.request.ReturnToSenderRequest.class);
        verify(orderService).returnToSender(eq(88L), eq(8L), captor.capture());
        assertEquals("RTN-1", captor.getValue().getReturnTrackingNumber());
        assertEquals(2, captor.getValue().getEvidenceImages().size());
        assertEquals("note", captor.getValue().getNote());
    }

    @Test
    void getSellerOrders_capsSizeTo100() {
        UserDetailsImpl user = user(9L, "SELLER");
        PageResponse<OrderSummaryResponse> pageResponse = PageResponse.<OrderSummaryResponse>builder()
                .content(List.of())
                .page(0)
                .size(100)
                .totalElements(0)
                .totalPages(0)
                .last(true)
                .build();
        when(orderService.getSellerOrders(eq(9L), eq("PAID"), any(), any(), eq(1), eq(100)))
                .thenReturn(pageResponse);

        ResponseEntity<com.flashsale.commonlib.dto.ApiResponse<PageResponse<OrderSummaryResponse>>> result =
                controller.getSellerOrders(user, "PAID", LocalDate.now().minusDays(5), LocalDate.now(), 1, 500);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertTrue(result.getBody().isSuccess());
        verify(orderService).getSellerOrders(eq(9L), eq("PAID"), any(LocalDateTime.class), any(LocalDateTime.class), eq(1), eq(100));
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
