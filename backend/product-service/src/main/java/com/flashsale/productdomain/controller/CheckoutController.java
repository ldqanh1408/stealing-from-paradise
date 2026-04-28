package com.flashsale.productdomain.controller;

import com.flashsale.productdomain.dto.request.CheckoutPreviewRequest;
import com.flashsale.productdomain.dto.request.PlaceOrderRequest;
import com.flashsale.productdomain.dto.response.CheckoutPreviewResponse;
import com.flashsale.productdomain.service.CheckoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutService checkoutService;

    @PostMapping("/preview")
    public ResponseEntity<CheckoutPreviewResponse> checkoutPreview(
            @RequestHeader("X-Customer-Id") Long customerId,
            @RequestBody CheckoutPreviewRequest request) {
        return ResponseEntity.ok(checkoutService.checkoutPreview(customerId, request));
    }

    @PostMapping("/place-order")
    public ResponseEntity<Void> placeOrder(
            @RequestHeader("X-Customer-Id") Long customerId,
            @RequestBody PlaceOrderRequest request) {
        checkoutService.placeOrder(customerId, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/preview")
    public ResponseEntity<Void> cancelPreview(@RequestHeader("X-Customer-Id") Long customerId) {
        checkoutService.cancelPreview(customerId);
        return ResponseEntity.noContent().build();
    }
}
