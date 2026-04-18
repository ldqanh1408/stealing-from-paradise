package com.flashsale.paymentdomain.controller;

import com.flashsale.commonlib.dto.ApiResponse;
import com.flashsale.commonlib.dto.PageResponse;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.paymentdomain.dto.request.AdminRefundApproveRequest;
import com.flashsale.paymentdomain.dto.request.AdminRefundRejectRequest;
import com.flashsale.paymentdomain.dto.response.AdminRefundApproveResponse;
import com.flashsale.paymentdomain.dto.response.RefundDetailResponse;
import com.flashsale.paymentdomain.dto.response.RefundListResponse;
import com.flashsale.paymentdomain.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/refunds")
@RequiredArgsConstructor
@Slf4j
public class AdminRefundController {

    private final RefundService refundService;

    /**
     * GET /api/v1/admin/refunds
     * Danh sách tất cả yêu cầu hoàn tiền (Admin).
     *
     * Query params:
     * - status: PENDING | SUCCESS | FAILED | REJECTED
     * - type: FULL | PARTIAL
     * - seller_id: long (filter by affected seller — for groupRef lookup)
     * - group_ref: uuid
     * - from_date / to_date: ISO 8601 date (yyyy-MM-dd)
     * - page, size: pagination
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<RefundListResponse>>> listRefunds(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(name = "from_date", required = false) String fromDate,
            @RequestParam(name = "to_date", required = false) String toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("Admin list refunds: status={}, type={}, page={}", status, type, page);
        PageResponse<RefundListResponse> result = refundService.listAllRefunds(status, type, fromDate, toDate, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * GET /api/v1/admin/refunds/{refundId}
     * Chi tiết một yêu cầu hoàn tiền (Admin).
     * Trả về đầy đủ thông tin refund, danh sách items, tracking number, return evidence.
     */
    @GetMapping("/{refundId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RefundDetailResponse>> getRefund(
            @PathVariable Long refundId) {

        log.info("Admin get refund detail: refundId={}", refundId);
        RefundDetailResponse response = refundService.getRefundById(refundId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/v1/admin/refunds/{refundId}/approve
     * Duyệt hoàn tiền thủ công (Admin).
     *
     * Side effects:
     * 1. Stripe refunds.create (dùng adjust_amount nếu có)
     * 2. REFUNDS.status = SUCCESS
     * 3. Nếu tracking_number → UPDATE REFUND_ITEMS
     * 4. Publish refund.admin_approved (kèm tracking_number)
     * 5. trust_score[seller] -= 5 nếu caused_by=SELLER (handled by identity-service via Kafka)
     */
    @PostMapping("/{refundId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminRefundApproveResponse>> approveRefund(
            @PathVariable Long refundId,
            @Valid @RequestBody AdminRefundApproveRequest request,
            @AuthenticationPrincipal UserDetailsImpl admin) {

        log.info("Admin {} approving refund {}", admin.getId(), refundId);
        AdminRefundApproveResponse response = refundService.approveRefund(refundId, admin.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(response, "Refund approved successfully"));
    }

    /**
     * POST /api/v1/admin/refunds/{refundId}/reject
     * Từ chối yêu cầu hoàn tiền (Admin).
     *
     * Side effects:
     * 1. REFUNDS.status = REJECTED
     * 2. Publish refund.rejected
     * 3. Nếu fraud_evidence=true → trust_score[buyer] -= delta (handled by identity-service)
     * 4. Push notification đến Buyer
     */
    @PostMapping("/{refundId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> rejectRefund(
            @PathVariable Long refundId,
            @Valid @RequestBody AdminRefundRejectRequest request,
            @AuthenticationPrincipal UserDetailsImpl admin) {

        log.info("Admin {} rejecting refund {}", admin.getId(), refundId);
        refundService.rejectRefund(refundId, admin.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(null, "Refund rejected"));
    }
}
