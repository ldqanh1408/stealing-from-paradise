package com.flashsale.productservice.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.productservice.dto.request.InventoryAdjustRequest;
import com.flashsale.productservice.dto.request.InventoryRestockRequest;
import com.flashsale.productservice.dto.response.InventoryResponse;
import com.flashsale.productservice.service.InventoryManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Slf4j
public class InventoryController {

    private final InventoryManagementService inventoryService;

    // ─── Public / Authenticated: GET /inventory/{skuCode} ────────────────────

    @GetMapping("/inventory/{skuCode}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<InventoryResponse>> getInventory(@PathVariable String skuCode) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getInventory(skuCode)));
    }

    // ─── Seller: PUT /inventory/{skuCode}/restock ─────────────────────────────

    @PutMapping("/inventory/{skuCode}/restock")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<InventoryResponse>> restock(
            @PathVariable String skuCode,
            @AuthenticationPrincipal UserDetailsImpl user,
            @Valid @RequestBody InventoryRestockRequest req) {
        InventoryResponse result = inventoryService.restock(skuCode, user.getId(), req);
        return ResponseEntity.ok(ApiResponse.success(result, "Nhập thêm hàng thành công"));
    }

    // ─── Seller: POST /seller/inventory/adjust ───────────────────────────────

    @PostMapping("/seller/inventory/adjust")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<InventoryResponse>> adjust(
            @AuthenticationPrincipal UserDetailsImpl user,
            @Valid @RequestBody InventoryAdjustRequest req) {
        InventoryResponse result = inventoryService.adjust(user.getId(), req);
        return ResponseEntity.ok(ApiResponse.success(result, "Điều chỉnh tồn kho thành công"));
    }
}
