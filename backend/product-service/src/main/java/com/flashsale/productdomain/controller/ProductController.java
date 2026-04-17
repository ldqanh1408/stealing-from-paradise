package com.flashsale.productdomain.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.productdomain.dto.request.CreateProductRequest;
import com.flashsale.productdomain.dto.request.UpdateProductRequest;
import com.flashsale.productdomain.dto.response.PresignedUrlResponse;
import com.flashsale.productdomain.dto.response.ProductResponse;
import com.flashsale.productdomain.service.MinioService;
import com.flashsale.productdomain.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class ProductController {

    private final ProductService productService;
    private final MinioService minioService;

    // ─── Public: GET /products/{productId} ───────────────────────────────────

    @GetMapping("/products/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable String productId) {
        return ResponseEntity.ok(ApiResponse.success(productService.getProduct(productId)));
    }

    // ─── Seller: POST /products ───────────────────────────────────────────────

    @PostMapping("/products")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @AuthenticationPrincipal UserDetailsImpl user,
            @Valid @RequestBody CreateProductRequest req) {
        ProductResponse created = productService.createProduct(Long.parseLong(user.getId()), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    // ─── Seller: PUT /products/{productId} ───────────────────────────────────

    @PutMapping("/products/{productId}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable String productId,
            @AuthenticationPrincipal UserDetailsImpl user,
            @Valid @RequestBody UpdateProductRequest req) {
        ProductResponse updated = productService.updateProduct(productId, Long.parseLong(user.getId()), req);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    // ─── Seller: DELETE /products/{productId} ────────────────────────────────

    @DeleteMapping("/products/{productId}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @PathVariable String productId,
            @AuthenticationPrincipal UserDetailsImpl user) {
        productService.deleteProduct(productId, Long.parseLong(user.getId()));
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa mềm sản phẩm thành công"));
    }

    // ─── Seller: GET /products/{productId}/presigned-url ─────────────────────

    @GetMapping("/products/{productId}/presigned-url")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<PresignedUrlResponse>> getPresignedUrl(
            @PathVariable String productId,
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestParam @NotBlank String fileName,
            @RequestParam @NotBlank String contentType) {
        PresignedUrlResponse url = minioService.generatePresignedUrl(
                Long.parseLong(user.getId()), productId, fileName, contentType);
        return ResponseEntity.ok(ApiResponse.success(url));
    }

    // ─── Seller: GET /sellers/me/products ────────────────────────────────────

    @GetMapping("/sellers/me/products")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> getMyProducts(
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ProductResponse> result = productService.getSellerProducts(Long.parseLong(user.getId()), page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
