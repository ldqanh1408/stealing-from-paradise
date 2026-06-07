package com.flashsale.refundservice.config;

import com.flashsale.commonlib.config.DevDataProperties;
import com.flashsale.refundservice.domain.model.Refund;
import com.flashsale.refundservice.domain.model.RefundItem;
import com.flashsale.refundservice.domain.repository.RefundItemRepository;
import com.flashsale.refundservice.domain.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds refund records linked to the order-service / payment-service seed data.
 *
 * <p>Order IDs (sub-orders) and transaction IDs are auto-generated 1..10 by the
 * other seeders when run against a fresh database. We reference:</p>
 * <ul>
 *   <li>Refund 1 — Order 1, txn 1 — APPROVED full refund (item arrived broken)</li>
 *   <li>Refund 2 — Order 2, txn 2 — PENDING partial (1 item missing from delivery)</li>
 *   <li>Refund 3 — Order 4, txn 4 — RTS_COMPLETED full refund (returned + refunded)</li>
 *   <li>Refund 4 — Order 9, txn 8 — REJECTED (proof insufficient)</li>
 * </ul>
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "dev-data.enabled", havingValue = "true", matchIfMissing = false)
public class RefundDevDataLoader implements CommandLineRunner {

    private final RefundRepository refundRepository;
    private final RefundItemRepository refundItemRepository;
    private final DevDataProperties devDataProperties;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("[RefundDevDataLoader] Starting dev data seed for refund-service...");

        if (devDataProperties.isReset()) {
            log.warn("[RefundDevDataLoader] RESET=true — wiping all refund data...");
            refundItemRepository.deleteAllInBatch();
            refundRepository.deleteAllInBatch();
            log.info("[RefundDevDataLoader] All refund data wiped.");
        } else if (refundRepository.count() > 0) {
            log.info("[RefundDevDataLoader] Data already exists, skipping. Set dev-data.reset=true to reload.");
            return;
        }

        // ---- Refund 1: APPROVED full refund on Order 1 (delivered) ----
        Refund r1 = refundRepository.save(Refund.builder()
                .transactionId(1L)
                .orderId(1L)
                .userId(1L)
                .type("FULL")
                .initiatedBy("BUYER")
                .refundReasonType("ITEM_BROKEN")
                .amount(new BigDecimal("250000.00"))
                .reason("Sản phẩm bị vỡ màn hình khi mở hộp.")
                .status("RTS_COMPLETED")
                .reviewedBy(10L)
                .refundRef("re_test_001_AABBCC")
                .build());

        // ---- Refund 2: PENDING partial on Order 2 ----
        Refund r2 = refundRepository.save(Refund.builder()
                .transactionId(2L)
                .orderId(2L)
                .userId(2L)
                .type("PARTIAL")
                .initiatedBy("BUYER")
                .refundReasonType("MISSING_ITEM")
                .amount(new BigDecimal("600000.00"))
                .reason("Thiếu 2 MagSafe Charger trong đơn.")
                .status("PENDING")
                .build());
        refundItemRepository.save(RefundItem.builder()
                .refundId(r2.getId())
                .itemId(1L)
                .quantity(2)
                .refundAmount(new BigDecimal("600000.00"))
                .itemReason("Thiếu hàng so với đơn đặt.")
                .status("PENDING")
                .build());

        // ---- Refund 3: RTS_COMPLETED full refund on Order 4 ----
        Refund r3 = refundRepository.save(Refund.builder()
                .transactionId(4L)
                .orderId(4L)
                .userId(4L)
                .type("FULL")
                .initiatedBy("BUYER")
                .refundReasonType("CHANGE_OF_MIND")
                .amount(new BigDecimal("3450000.00"))
                .reason("Đổi ý sau khi mở hộp, hàng còn nguyên seal.")
                .status("RTS_COMPLETED")
                .reviewedBy(10L)
                .refundRef("re_test_003_GGHHII")
                .build());
        refundItemRepository.saveAll(List.of(
                RefundItem.builder().refundId(r3.getId()).itemId(4L)
                        .quantity(1).refundAmount(new BigDecimal("2450000.00"))
                        .itemReason("Returned").status("COMPLETED")
                        .returnTrackingNumber("GHN-RTS-001").build(),
                RefundItem.builder().refundId(r3.getId()).itemId(5L)
                        .quantity(1).refundAmount(new BigDecimal("1000000.00"))
                        .itemReason("Returned").status("COMPLETED")
                        .returnTrackingNumber("GHN-RTS-001").build()
        ));

        // ---- Refund 4: REJECTED on Order 9 ----
        Refund r4 = refundRepository.save(Refund.builder()
                .transactionId(8L)
                .orderId(9L)
                .userId(8L)
                .type("PARTIAL")
                .initiatedBy("BUYER")
                .refundReasonType("ITEM_NOT_AS_DESCRIBED")
                .amount(new BigDecimal("1200000.00"))
                .reason("Sản phẩm khác mô tả.")
                .status("REJECTED")
                .rejectReason("Không cung cấp đủ ảnh chứng minh sản phẩm khác mô tả.")
                .reviewedBy(10L)
                .adminNote("Yêu cầu khách gửi thêm video unbox.")
                .build());

        log.info("[RefundDevDataLoader] Seeded 4 refunds (1 RTS_COMPLETED, 1 PENDING, 1 RTS_COMPLETED+items, 1 REJECTED).");
    }
}
