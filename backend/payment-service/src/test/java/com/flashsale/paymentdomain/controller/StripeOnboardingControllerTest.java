package com.flashsale.paymentdomain.controller;

import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.paymentdomain.dto.response.StripeOnboardingResponse;
import com.flashsale.paymentdomain.dto.response.StripeOnboardingStatusResponse;
import com.flashsale.paymentdomain.service.StripeOnboardingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeOnboardingControllerTest {

    @Mock
    private StripeOnboardingService stripeOnboardingService;

    private StripeOnboardingController controller;

    @BeforeEach
    void setUp() {
        controller = new StripeOnboardingController(stripeOnboardingService);
    }

    @Test
    void startOnboarding_returnsCreated() {
        UserDetailsImpl seller = user(1L, "SELLER");
        StripeOnboardingResponse serviceResponse = StripeOnboardingResponse.builder()
                .stripeAccountId("acct_1")
                .onboardingUrl("https://stripe.test/start")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(stripeOnboardingService.startOnboarding(1L)).thenReturn(serviceResponse);

        ResponseEntity<com.flashsale.commonlib.dto.ApiResponse<StripeOnboardingResponse>> result =
                controller.startOnboarding(seller);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertTrue(result.getBody().isSuccess());
        assertEquals("acct_1", result.getBody().getData().getStripeAccountId());
    }

    @Test
    void getOnboardingStatus_returnsData() {
        UserDetailsImpl seller = user(2L, "SELLER");
        StripeOnboardingStatusResponse serviceResponse = StripeOnboardingStatusResponse.builder()
                .stripeAccountId("acct_2")
                .onboardingStatus("COMPLETE")
                .chargesEnabled(true)
                .payoutsEnabled(true)
                .build();
        when(stripeOnboardingService.getOnboardingStatus(2L)).thenReturn(serviceResponse);

        ResponseEntity<com.flashsale.commonlib.dto.ApiResponse<StripeOnboardingStatusResponse>> result =
                controller.getOnboardingStatus(seller);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals("COMPLETE", result.getBody().getData().getOnboardingStatus());
    }

    @Test
    void refreshOnboardingLink_returnsData() {
        UserDetailsImpl seller = user(3L, "SELLER");
        StripeOnboardingResponse serviceResponse = StripeOnboardingResponse.builder()
                .stripeAccountId("acct_3")
                .onboardingUrl("https://stripe.test/refresh")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(stripeOnboardingService.refreshOnboardingLink(3L)).thenReturn(serviceResponse);

        ResponseEntity<com.flashsale.commonlib.dto.ApiResponse<StripeOnboardingResponse>> result =
                controller.refreshOnboardingLink(seller);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertTrue(result.getBody().isSuccess());
        assertEquals("https://stripe.test/refresh", result.getBody().getData().getOnboardingUrl());
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
