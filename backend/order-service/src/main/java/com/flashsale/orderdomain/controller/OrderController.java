package com.flashsale.orderdomain.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.orderdomain.service.OrderService;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    public ResponseEntity<ApiResponse<String>> getMyOrders(
            @RequestHeader("X-User-Id") String userId) {
        log.info("Getting orders for user: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("orders list"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<String>> createOrder(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody String orderRequest) {
        log.info("Creating order for user: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("order created"));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<String>> getOrder(
            @PathVariable String orderId,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Getting order: {}", orderId);
        return ResponseEntity.ok(ApiResponse.success("order detail"));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<String>> cancelOrder(
            @PathVariable String orderId,
            @RequestHeader("X-User-Id") String userId) {
        log.info("Cancelling order: {}", orderId);
        return ResponseEntity.ok(ApiResponse.success("order cancelled"));
    }
}

