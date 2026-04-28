package com.flashsale.productdomain.controller;

import com.flashsale.productdomain.domain.model.Product;
import com.flashsale.productdomain.domain.model.Sku;
import com.flashsale.productdomain.dto.request.CreateProductRequest;
import com.flashsale.productdomain.dto.request.UpdateProductRequest;
import com.flashsale.productdomain.dto.request.UpdateSkuRequest;
import com.flashsale.productdomain.dto.response.ProductDetailResponse;
import com.flashsale.productdomain.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/{slug}")
    public ResponseEntity<ProductDetailResponse> getProductDetail(@PathVariable String slug) {
        return ResponseEntity.ok(productService.getProductDetail(slug));
    }

    @PostMapping
    public ResponseEntity<Product> createProduct(
            @RequestHeader("X-Seller-Id") Long sellerId,
            @RequestBody CreateProductRequest request) {
        return ResponseEntity.ok(productService.createProduct(sellerId, request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Product> updateProduct(
            @PathVariable UUID id,
            @RequestBody UpdateProductRequest request) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @PatchMapping("/skus/{skuId}")
    public ResponseEntity<Sku> updateSku(
            @PathVariable UUID skuId,
            @RequestBody UpdateSkuRequest request) {
        return ResponseEntity.ok(productService.updateSku(skuId, request));
    }
}
