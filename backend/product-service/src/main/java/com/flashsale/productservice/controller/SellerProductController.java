package com.flashsale.productservice.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.productservice.dto.request.CreateVariantRequest;
import com.flashsale.productservice.dto.request.UpdateVariantRequest;
import com.flashsale.productservice.dto.response.ProductResponse;
import com.flashsale.productservice.dto.response.VariantResponse;
import com.flashsale.productservice.service.ProductService;
import com.flashsale.productservice.service.VariantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/seller")
@RequiredArgsConstructor
@Slf4j
public class SellerProductController {

    private final VariantService variantService;
    private final ProductService productService;

    // ─── Product CRUD ──────────────────────────────────────────────────────────

    @DeleteMapping("/products/{productId}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @PathVariable String productId,
            @AuthenticationPrincipal UserDetailsImpl user) {
        productService.deleteProduct(productId, user.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa mềm sản phẩm thành công"));
    }

    // ─── Variant CRUD ─────────────────────────────────────────────────────────

    @GetMapping("/products/{productId}/variants")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<List<VariantResponse>>> listVariants(
            @PathVariable String productId,
            @AuthenticationPrincipal UserDetailsImpl user) {
        List<VariantResponse> variants = variantService.listVariants(productId, user.getId());
        return ResponseEntity.ok(ApiResponse.success(variants));
    }

    @PostMapping("/products/{productId}/variants")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<VariantResponse>> createVariant(
            @PathVariable String productId,
            @AuthenticationPrincipal UserDetailsImpl user,
            @Valid @RequestBody CreateVariantRequest req) {
        VariantResponse created = variantService.createVariant(productId, user.getId(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @PutMapping("/variants/{variantId}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<VariantResponse>> updateVariant(
            @PathVariable String variantId,
            @AuthenticationPrincipal UserDetailsImpl user,
            @Valid @RequestBody UpdateVariantRequest req) {
        VariantResponse updated = variantService.updateVariant(variantId, user.getId(), req);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @DeleteMapping("/variants/{variantId}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<Void>> deleteVariant(
            @PathVariable String variantId,
            @AuthenticationPrincipal UserDetailsImpl user) {
        variantService.deleteVariant(variantId, user.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa variant thành công"));
    }

    // ─── Product Lifecycle ────────────────────────────────────────────────────

    @PostMapping("/products/{productId}/submit")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<ProductResponse>> submitForReview(
            @PathVariable String productId,
            @AuthenticationPrincipal UserDetailsImpl user) {
        ProductResponse result = productService.submitForReview(productId, user.getId());
        return ResponseEntity.ok(ApiResponse.success(result, "Sản phẩm đã được gửi duyệt"));
    }

    @PostMapping("/products/{productId}/publish")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<ProductResponse>> publishProduct(
            @PathVariable String productId,
            @AuthenticationPrincipal UserDetailsImpl user) {
        ProductResponse result = productService.publishProduct(productId, user.getId());
        return ResponseEntity.ok(ApiResponse.success(result, "Mở bán sản phẩm thành công"));
    }

    @PostMapping("/products/{productId}/unpublish")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<ProductResponse>> unpublishProduct(
            @PathVariable String productId,
            @AuthenticationPrincipal UserDetailsImpl user) {
        ProductResponse result = productService.unpublishProduct(productId, user.getId());
        return ResponseEntity.ok(ApiResponse.success(result, "Tạm ẩn sản phẩm thành công"));
    }
}
