package com.flashsale.productdomain.controller;

import com.flashsale.productdomain.dto.request.AddCartItemRequest;
import com.flashsale.productdomain.dto.request.UpdateCartItemRequest;
import com.flashsale.productdomain.dto.response.CartItemResponse;
import com.flashsale.productdomain.dto.response.CartResponse;
import com.flashsale.productdomain.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<CartResponse> getCart(@RequestHeader("X-Customer-Id") Long customerId) {
        return ResponseEntity.ok(cartService.getCart(customerId));
    }

    @PostMapping("/items")
    public ResponseEntity<CartItemResponse> addToCart(
            @RequestHeader("X-Customer-Id") Long customerId,
            @RequestBody AddCartItemRequest request) {
        return ResponseEntity.ok(cartService.addToCart(customerId, request));
    }

    @PatchMapping("/items/{skuId}")
    public ResponseEntity<CartItemResponse> updateCartItem(
            @RequestHeader("X-Customer-Id") Long customerId,
            @PathVariable UUID skuId,
            @RequestBody UpdateCartItemRequest request) {
        return ResponseEntity.ok(cartService.updateCartItem(customerId, skuId, request));
    }

    @DeleteMapping("/items/{skuId}")
    public ResponseEntity<Void> removeFromCart(
            @RequestHeader("X-Customer-Id") Long customerId,
            @PathVariable UUID skuId) {
        cartService.removeFromCart(customerId, skuId);
        return ResponseEntity.noContent().build();
    }
}
