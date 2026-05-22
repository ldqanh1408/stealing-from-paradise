package com.flashsale.productservice.service;

import com.flashsale.commonlib.exception.AppException;
import com.flashsale.productservice.domain.model.Product;
import com.flashsale.productservice.domain.repository.CategoryRepository;
import com.flashsale.productservice.domain.repository.InventoryRepository;
import com.flashsale.productservice.domain.repository.ProductRepository;
import com.flashsale.productservice.domain.repository.ProductVariantRepository;
import com.flashsale.productservice.dto.request.AdminApproveRequest;
import com.flashsale.productservice.dto.request.AdminRejectRequest;
import com.flashsale.productservice.dto.response.ProductResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ProductVariantRepository variantRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private KafkaProducerService kafkaProducer;
    @InjectMocks private AdminProductService adminService;

    private Product pendingProduct;

    @BeforeEach
    void setUp() {
        pendingProduct = Product.builder()
                .id("prod-1")
                .sellerId(1L)
                .categoryId("cat-1")
                .name("Test Product")
                .status(AdminProductService.PENDING)  // UPPERCASE
                .rejectCount(0)
                .build();
    }

    // ─── approveProduct tests ─────────────────────────────────────────────────

    @Test
    void approveProduct_pendingProduct_setsApprovedStatus() {
        AdminApproveRequest req = AdminApproveRequest.builder()
                .note("Looks good!")
                .build();

        when(productRepository.findByIdAndDeletedAtIsNull("prod-1"))
                .thenReturn(Optional.of(pendingProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(variantRepository.findByProductId("prod-1")).thenReturn(List.of());
        when(inventoryRepository.findByVariantCode(anyString())).thenReturn(Optional.empty());
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.empty());

        ProductResponse result = adminService.approveProduct("prod-1", req, 999L);

        assertThat(result.getStatus()).isEqualTo(AdminProductService.APPROVED);

        verify(productRepository).save(argThat(p ->
            p.getStatus().equals(AdminProductService.APPROVED)
            && p.getReviewedAt() != null
            && p.getReviewedBy().equals(999L)
            && p.getRejectReason() == null
            && p.getRejectCount() == 0
        ));
    }

    @Test
    void approveProduct_nonPending_throwsException() {
        pendingProduct.setStatus(AdminProductService.DRAFT);  // Not PENDING
        when(productRepository.findByIdAndDeletedAtIsNull("prod-1"))
                .thenReturn(Optional.of(pendingProduct));

        assertThatThrownBy(() -> adminService.approveProduct("prod-1", new AdminApproveRequest(), 999L))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("PENDING");
    }

    // ─── rejectProduct tests ─────────────────────────────────────────────────

    @Test
    void rejectProduct_validReason_setsRejectedStatus() {
        AdminRejectRequest req = AdminRejectRequest.builder()
                .reason("Sản phẩm không đúng quy cách, vui lòng cập nhật lại")
                .note("Lần từ chối đầu tiên")
                .build();

        when(productRepository.findByIdAndDeletedAtIsNull("prod-1"))
                .thenReturn(Optional.of(pendingProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(variantRepository.findByProductId("prod-1")).thenReturn(List.of());
        when(inventoryRepository.findByVariantCode(anyString())).thenReturn(Optional.empty());
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.empty());

        ProductResponse result = adminService.rejectProduct("prod-1", req, 999L);

        assertThat(result.getStatus()).isEqualTo(AdminProductService.REJECTED);

        verify(productRepository).save(argThat(p ->
            p.getStatus().equals(AdminProductService.REJECTED)
            && p.getRejectReason().equals(req.getReason())
            && p.getReviewedAt() != null
            && p.getReviewedBy().equals(999L)
            && p.getRejectCount() == 1
        ));
    }

    @Test
    void rejectProduct_reasonTooShort_throwsException() {
        AdminRejectRequest req = AdminRejectRequest.builder()
                .reason("Ngắn")
                .build();

        when(productRepository.findByIdAndDeletedAtIsNull("prod-1"))
                .thenReturn(Optional.of(pendingProduct));

        assertThatThrownBy(() -> adminService.rejectProduct("prod-1", req, 999L))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("10 ký tự");
    }

    @Test
    void rejectProduct_exceedsLimit_throwsException() {
        pendingProduct.setRejectCount(3);
        AdminRejectRequest req = AdminRejectRequest.builder()
                .reason("Lý do đủ dài để bị từ chối lần thứ 4")
                .build();

        when(productRepository.findByIdAndDeletedAtIsNull("prod-1"))
                .thenReturn(Optional.of(pendingProduct));

        assertThatThrownBy(() -> adminService.rejectProduct("prod-1", req, 999L))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("giới hạn");
    }
}
