package com.flashsale.paymentdomain.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.paymentdomain.service.PaymentService;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/create-intent")
    public ResponseEntity<ApiResponse<String>> createPaymentIntent(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody String request) {
        log.info("Creating payment intent for user: {}", userId);
        // TODO: Call Stripe API to create PaymentIntent
        return ResponseEntity.ok(ApiResponse.success("{\"clientSecret\": \"pi_xxx\"}"));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<String>> getPayment(
            @PathVariable String paymentId,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Getting payment: {}", paymentId);
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

