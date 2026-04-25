package com.flashsale.identitydomain.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.dto.PageResponse;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.identitydomain.dto.response.LoyaltyBalanceResponse;
import com.flashsale.identitydomain.dto.response.LoyaltyEstimateResponse;
import com.flashsale.identitydomain.dto.response.PointTransactionSummary;
import com.flashsale.identitydomain.service.LoyaltyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/v1/loyalty")
@RequiredArgsConstructor
@Slf4j
public class LoyaltyController {

    private final LoyaltyService loyaltyService;

    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<LoyaltyBalanceResponse>> getBalance(
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(ApiResponse.success(loyaltyService.getBalance(user.getId())));
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<PageResponse<PointTransactionSummary>>> getTransactions(
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return ResponseEntity.ok(ApiResponse.success(
                PageResponse.of(loyaltyService.getTransactions(user.getId(), type, status, pageable))));
    }

    @GetMapping("/estimate")
    public ResponseEntity<ApiResponse<LoyaltyEstimateResponse>> estimate(
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestParam BigDecimal orderAmount,
            @RequestParam(required = false) Integer pointsToUse) {
        return ResponseEntity.ok(ApiResponse.success(
                loyaltyService.getEstimate(orderAmount, pointsToUse, user.getId())));
    }
}
