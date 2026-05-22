package com.flashsale.productservice.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.productservice.dto.request.CartRequest;
import com.flashsale.productservice.dto.response.CartResponse;
import com.flashsale.productservice.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/cart")
@RequiredArgsConstructor
@Slf4j
public class CartController {

    private final CartService cartService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @AuthenticationPrincipal UserDetailsImpl user) {
        CartResponse cart = cartService.getCart(user.getId());
        return ResponseEntity.ok(ApiResponse.success(cart));
    }

    @PostMapping("/items")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CartResponse>> addItem(
            @AuthenticationPrincipal UserDetailsImpl user,
            @Valid @RequestBody CartRequest req) {
        CartResponse cart = cartService.addItemToCart(
                user.getId(), req.getSkuCode(), req.getQuantity(), req.getFsItemId());
        return ResponseEntity.ok(ApiResponse.success(cart));
    }

    @PutMapping("/items/{itemId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CartResponse>> updateItem(
            @AuthenticationPrincipal UserDetailsImpl user,
            @PathVariable String itemId,
            @Valid @RequestBody UpdateCartItemRequest req) {
        CartResponse cart = cartService.updateCartItem(user.getId(), itemId, req.getQuantity());
        return ResponseEntity.ok(ApiResponse.success(cart));
    }

    @DeleteMapping("/items/{itemId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> removeItem(
            @AuthenticationPrincipal UserDetailsImpl user,
            @PathVariable String itemId) {
        cartService.removeCartItem(user.getId(), itemId);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa sản phẩm khỏi giỏ hàng"));
    }

    @DeleteMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> clearCart(
            @AuthenticationPrincipal UserDetailsImpl user) {
        cartService.clearCart(user.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa toàn bộ giỏ hàng"));
    }

    @lombok.Data
    public static class UpdateCartItemRequest {
        @jakarta.validation.constraints.NotNull
        @jakarta.validation.constraints.Min(0)
        @jakarta.validation.constraints.Max(1000)
        private Integer quantity;
    }
}
