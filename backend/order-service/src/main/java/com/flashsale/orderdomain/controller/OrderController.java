package com.flashsale.orderdomain.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.orderdomain.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> getMyOrders(
            @AuthenticationPrincipal UserDetailsImpl user) {
        log.info("Getting orders for user: {}", user.getId());
        return ResponseEntity.ok(ApiResponse.success("orders list"));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> createOrder(
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestBody String orderRequest) {
        log.info("Creating order for user: {}", user.getId());
        return ResponseEntity.ok(ApiResponse.success("order created"));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> getOrder(
            @PathVariable String orderId,
            @AuthenticationPrincipal UserDetailsImpl user) {
        log.info("Getting order: {} for user: {}", orderId, user.getId());
        return ResponseEntity.ok(ApiResponse.success("order detail"));
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> cancelOrder(
            @PathVariable String orderId,
            @AuthenticationPrincipal UserDetailsImpl user) {
        log.info("Cancelling order: {} for user: {}", orderId, user.getId());
        return ResponseEntity.ok(ApiResponse.success("order cancelled"));
    }
}

