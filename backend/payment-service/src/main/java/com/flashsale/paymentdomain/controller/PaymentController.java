package com.flashsale.paymentdomain.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.paymentdomain.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/create-intent")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> createPaymentIntent(
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestBody String request) {
        log.info("Creating payment intent for user: {}", user.getId());
        // TODO: Call Stripe API to create PaymentIntent
        return ResponseEntity.ok(ApiResponse.success("{\"clientSecret\": \"pi_xxx\"}"));
    }

    @GetMapping("/{paymentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> getPayment(
            @PathVariable String paymentId,
            @AuthenticationPrincipal UserDetailsImpl user) {
        log.info("Getting payment: {} for user: {}", paymentId, user.getId());
        return ResponseEntity.ok(ApiResponse.success("payment details"));
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature) {
        log.info("Received Stripe webhook");
        // TODO: Verify signature and process event
        return ResponseEntity.ok("received");
    }
}

