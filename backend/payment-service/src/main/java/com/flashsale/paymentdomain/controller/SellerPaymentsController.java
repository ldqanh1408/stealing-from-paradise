package com.flashsale.paymentdomain.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.paymentdomain.dto.response.SellerEarningsResponse;
import com.flashsale.paymentdomain.dto.response.SellerStripeDashboardResponse;
import com.flashsale.paymentdomain.service.SellerPaymentsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/seller/payments")
@RequiredArgsConstructor
@Slf4j
public class SellerPaymentsController {

    private final SellerPaymentsService sellerPaymentsService;

    /**
     * GET /api/v1/seller/payments/earnings
     * Lay danh sach tat ca earnings (SellerTransfer) cua seller hien tai.
     */
    @GetMapping("/earnings")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<SellerEarningsResponse>> getEarnings(
            @AuthenticationPrincipal UserDetailsImpl user) {

        log.info("Get seller earnings for userId={}", user.getId());
        SellerEarningsResponse response = sellerPaymentsService.getSellerEarnings(user.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/v1/seller/payments/stripe-dashboard
     * Lay Stripe Dashboard login link (Single-Use Login Link) cho seller.
     */
    @GetMapping("/stripe-dashboard")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ApiResponse<SellerStripeDashboardResponse>> getStripeDashboardLink(
            @AuthenticationPrincipal UserDetailsImpl user) {

        log.info("Get Stripe dashboard link for userId={}", user.getId());
        SellerStripeDashboardResponse response = sellerPaymentsService.getStripeDashboardUrl(user.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
