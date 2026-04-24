package com.flashsale.productdomain.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.productdomain.dto.request.AdminApproveRequest;
import com.flashsale.productdomain.dto.request.AdminRejectRequest;
import com.flashsale.productdomain.dto.response.ProductResponse;
import com.flashsale.productdomain.service.AdminProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/admin/products")
@RequiredArgsConstructor
@Slf4j
public class AdminProductController {

    private final AdminProductService adminProductService;

    // ─── GET /admin/products/pending ──────────────────────────────────────────

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> getPendingProducts(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ProductResponse> result = adminProductService.getPendingProducts(categoryId, sellerId, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ─── POST /admin/products/{productId}/approve ─────────────────────────────

    @PostMapping("/{productId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> approveProduct(
            @PathVariable String productId,
            @RequestBody(required = false) AdminApproveRequest req) {
        if (req == null) req = new AdminApproveRequest();
        ProductResponse result = adminProductService.approveProduct(productId, req);
        return ResponseEntity.ok(ApiResponse.success(result, "Duyệt sản phẩm thành công"));
    }

    // ─── POST /admin/products/{productId}/reject ──────────────────────────────

    @PostMapping("/{productId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> rejectProduct(
            @PathVariable String productId,
            @Valid @RequestBody AdminRejectRequest req) {
        ProductResponse result = adminProductService.rejectProduct(productId, req);
        return ResponseEntity.ok(ApiResponse.success(result, "Từ chối sản phẩm thành công"));
    }
}
