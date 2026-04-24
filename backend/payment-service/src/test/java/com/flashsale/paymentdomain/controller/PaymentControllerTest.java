package com.flashsale.paymentdomain.controller;

import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.paymentdomain.dto.response.ClientSecretResponse;
import com.flashsale.paymentdomain.dto.response.TransactionDetailResponse;
import com.flashsale.paymentdomain.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    private PaymentController controller;

    @BeforeEach
    void setUp() {
        controller = new PaymentController(paymentService);
    }

    @Test
    void getTransactionByParentOrder_returnsData() {
        UserDetailsImpl user = user(1L, "BUYER");
        TransactionDetailResponse serviceResponse = TransactionDetailResponse.builder()
                .transactionId(10L)
                .parentOrderId(100L)
                .amount(BigDecimal.valueOf(20000))
                .status("PENDING")
                .build();
        when(paymentService.getTransactionByParentOrder(100L)).thenReturn(serviceResponse);

        ResponseEntity<com.flashsale.commonlib.dto.ApiResponse<TransactionDetailResponse>> result =
                controller.getTransactionByParentOrder(100L, user);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertTrue(result.getBody().isSuccess());
        assertEquals(10L, result.getBody().getData().getTransactionId());
    }

    @Test
    void getClientSecret_returnsData() {
        UserDetailsImpl user = user(2L, "BUYER");
        ClientSecretResponse serviceResponse = ClientSecretResponse.builder()
                .parentOrderId(200L)
                .transactionId(20L)
                .clientSecret("secret")
                .status("PENDING")
                .build();
        when(paymentService.getClientSecret(200L)).thenReturn(serviceResponse);

        ResponseEntity<com.flashsale.commonlib.dto.ApiResponse<ClientSecretResponse>> result =
                controller.getClientSecret(200L, user);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertTrue(result.getBody().isSuccess());
        assertEquals("secret", result.getBody().getData().getClientSecret());
    }

    @Test
    void handleStripeWebhook_returnsReceived() {
        ResponseEntity<String> result = controller.handleStripeWebhook("{\"id\":\"evt_1\"}", "sig");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("received", result.getBody());
        verify(paymentService).handleStripeWebhook("{\"id\":\"evt_1\"}", "sig");
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
