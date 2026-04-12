package com.flashsale.productdomain.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.productdomain.service.ProductService;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Slf4j
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiResponse<String>> listProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Listing products - page: {}, size: {}", page, size);
        return ResponseEntity.ok(ApiResponse.success("products list"));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<String>> getProduct(
            @PathVariable String productId) {
        log.info("Getting product: {}", productId);
        return ResponseEntity.ok(ApiResponse.success("product detail"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<String>> createProduct(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody String productRequest) {
        log.info("Creating product for seller: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("product created"));
    }

    @PutMapping("/{productId}")
    public ResponseEntity<ApiResponse<String>> updateProduct(
            @PathVariable String productId,
            @RequestHeader("X-User-Id") String userId,
            @RequestBody String productRequest) {
        log.info("Updating product: {}", productId);
        return ResponseEntity.ok(ApiResponse.success("product updated"));
    }
}

