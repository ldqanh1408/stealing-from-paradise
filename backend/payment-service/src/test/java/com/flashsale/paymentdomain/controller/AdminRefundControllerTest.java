package com.flashsale.paymentdomain.controller;

import com.flashsale.commonlib.dto.PageResponse;
import com.flashsale.commonlib.security.UserDetailsImpl;
import com.flashsale.paymentdomain.dto.request.AdminRefundApproveRequest;
import com.flashsale.paymentdomain.dto.request.AdminRefundRejectRequest;
import com.flashsale.paymentdomain.dto.response.AdminRefundApproveResponse;
import com.flashsale.paymentdomain.dto.response.RefundDetailResponse;
import com.flashsale.paymentdomain.dto.response.RefundListResponse;
import com.flashsale.paymentdomain.service.RefundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminRefundControllerTest {

    @Mock
    private RefundService refundService;

    private AdminRefundController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminRefundController(refundService);
    }

    @Test
    void listRefunds_returnsData() {
        PageResponse<RefundListResponse> page = PageResponse.<RefundListResponse>builder()
                .content(List.of(RefundListResponse.builder()
                        .refundId(1L)
                        .status("PENDING")
                        .amount(BigDecimal.valueOf(1000))
                        .build()))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .last(true)
                .build();
        when(refundService.listAllRefunds("PENDING", "PARTIAL", "2026-01-01", "2026-01-31", 0, 20))
                .thenReturn(page);

        ResponseEntity<com.flashsale.commonlib.dto.ApiResponse<PageResponse<RefundListResponse>>> result =
                controller.listRefunds("PENDING", "PARTIAL", "2026-01-01", "2026-01-31", 0, 20);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertTrue(result.getBody().isSuccess());
        assertEquals(1, result.getBody().getData().getContent().size());
    }

    @Test
    void getRefund_returnsData() {
        RefundDetailResponse serviceResponse = RefundDetailResponse.builder()
                .refundId(2L)
                .status("PENDING")
                .build();
        when(refundService.getRefundById(2L)).thenReturn(serviceResponse);

        ResponseEntity<com.flashsale.commonlib.dto.ApiResponse<RefundDetailResponse>> result =
                controller.getRefund(2L);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(2L, result.getBody().getData().getRefundId());
    }

    @Test
    void approveRefund_returnsData() {
        UserDetailsImpl admin = user(10L, "ADMIN");
        AdminRefundApproveRequest req = new AdminRefundApproveRequest();
        req.setAdminNote("approve");
        AdminRefundApproveResponse serviceResponse = AdminRefundApproveResponse.builder()
                .refundId(3L)
                .status("SUCCESS")
                .build();
        when(refundService.approveRefund(3L, 10L, req)).thenReturn(serviceResponse);

        ResponseEntity<com.flashsale.commonlib.dto.ApiResponse<AdminRefundApproveResponse>> result =
                controller.approveRefund(3L, req, admin);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertTrue(result.getBody().isSuccess());
        assertEquals("SUCCESS", result.getBody().getData().getStatus());
    }

    @Test
    void rejectRefund_returnsSuccessMessage() {
        UserDetailsImpl admin = user(11L, "ADMIN");
        AdminRefundRejectRequest req = new AdminRefundRejectRequest();
        req.setRejectReason("invalid claim");

        ResponseEntity<com.flashsale.commonlib.dto.ApiResponse<Void>> result =
                controller.rejectRefund(4L, req, admin);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertTrue(result.getBody().isSuccess());
        assertEquals("Refund rejected", result.getBody().getMessage());
        verify(refundService).rejectRefund(4L, 11L, req);
    }

    private UserDetailsImpl user(Long id, String role) {
        return UserDetailsImpl.builder()
                .id(id)
                .username("u" + id)
                .email("u" + id + "@test.local")
                .password("p")
                .role(role)
                .enabled(true)
                .build();
    }
}
