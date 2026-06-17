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
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbcTemplate;

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
            log.info("[RefundDevDataLoader] Data already exists, skipping main seed.");

            seedFeData();

            log.info("[RefundDevDataLoader] Dev data seed complete.");
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

        seedFeData();
    }

    private void seedFeData() {
        log.info("[RefundDevDataLoader] Seeding FE test-dataset...");

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM refund.refunds WHERE id >= 900201", Integer.class);
        if (count != null && count > 0) {
            log.info("[RefundDevDataLoader] FE data already exists, skipping.");
            return;
        }

        jdbcTemplate.update("INSERT INTO refund.refunds (id, transaction_id, order_id, user_id, group_ref, type, initiated_by, refund_reason_type, amount, reason, status, evidence_images, reject_reason, admin_note, reviewed_by, reviewed_at, refund_ref, raw_response, created_at, updated_at) VALUES " +
            "(900201, 900106, 900106, 900001, '90000000-0000-4000-c001-000000000201', 'PARTIAL', 'BUYER', 'MISSING_ITEM', 1990000, 'One accessory was missing from the package.', 'PENDING', '[\"https://picsum.photos/seed/fe-refund-pending/600/400\"]'::jsonb, null, null, null, null, null, '{}'::jsonb, now() - interval '6 hours', now()), " +
            "(900202, 900107, 900107, 900001, '90000000-0000-4000-c001-000000000202', 'FULL', 'BUYER', 'ITEM_BROKEN', 4990000, 'Product stopped working after delivery.', 'COMPLETED', '[\"https://picsum.photos/seed/fe-refund-completed/600/400\"]'::jsonb, null, 'Approved and refunded by admin fixture.', 900003, now() - interval '2 days', 're_fe_completed_900202', '{\"status\":\"succeeded\"}'::jsonb, now() - interval '3 days', now()), " +
            "(900203, 900104, 900104, 900001, '90000000-0000-4000-c001-000000000203', 'PARTIAL', 'BUYER', 'ITEM_NOT_AS_DESCRIBED', 390000, 'Requested refund after normal usage.', 'REJECTED', '[\"https://picsum.photos/seed/fe-refund-rejected/600/400\"]'::jsonb, 'Evidence does not show seller fault.', 'Reject fixture for admin screen.', 900003, now() - interval '1 day', null, '{}'::jsonb, now() - interval '2 days', now()), " +
            "(900204, 900108, 900108, 900001, '90000000-0000-4000-c001-000000000204', 'FULL', 'SYSTEM', 'RETURN_TO_SENDER', 27990000, 'Carrier returned package to seller.', 'PROCESSING', '[\"https://picsum.photos/seed/fe-refund-processing/600/400\"]'::jsonb, null, 'RTS automatic refund is processing.', 900003, now() - interval '12 hours', null, '{}'::jsonb, now() - interval '1 day', now()) " +
            "ON CONFLICT (id) DO UPDATE SET transaction_id=EXCLUDED.transaction_id,order_id=EXCLUDED.order_id,user_id=EXCLUDED.user_id,group_ref=EXCLUDED.group_ref,type=EXCLUDED.type,initiated_by=EXCLUDED.initiated_by,refund_reason_type=EXCLUDED.refund_reason_type,amount=EXCLUDED.amount,reason=EXCLUDED.reason,status=EXCLUDED.status,evidence_images=EXCLUDED.evidence_images,reject_reason=EXCLUDED.reject_reason,admin_note=EXCLUDED.admin_note,reviewed_by=EXCLUDED.reviewed_by,reviewed_at=EXCLUDED.reviewed_at,refund_ref=EXCLUDED.refund_ref,raw_response=EXCLUDED.raw_response,updated_at=now()");

        jdbcTemplate.update("INSERT INTO refund.refund_items (id, refund_id, item_id, quantity, refund_amount, item_reason, status, return_tracking_number, return_evidence_images, returned_at) VALUES " +
            "(900201, 900201, 900106, 1, 1990000, 'Missing item in box', 'PENDING', null, null, null), " +
            "(900202, 900202, 900107, 1, 4990000, 'Broken item returned', 'COMPLETED', 'FE-RTS-900202', '[\"https://picsum.photos/seed/fe-return-completed/600/400\"]'::jsonb, now() - interval '2 days'), " +
            "(900203, 900203, 900104, 1, 390000, 'Evidence rejected', 'REJECTED', null, null, null), " +
            "(900204, 900204, 900108, 1, 27990000, 'Carrier returned to sender', 'PROCESSING', 'FE-RTS-900204', '[\"https://picsum.photos/seed/fe-return-processing/600/400\"]'::jsonb, now() - interval '1 day') " +
            "ON CONFLICT (id) DO UPDATE SET refund_id=EXCLUDED.refund_id,item_id=EXCLUDED.item_id,quantity=EXCLUDED.quantity,refund_amount=EXCLUDED.refund_amount,item_reason=EXCLUDED.item_reason,status=EXCLUDED.status,return_tracking_number=EXCLUDED.return_tracking_number,return_evidence_images=EXCLUDED.return_evidence_images,returned_at=EXCLUDED.returned_at");

        jdbcTemplate.queryForObject("SELECT setval('refund.refunds_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM refund.refunds), 900204))", Long.class);
        jdbcTemplate.queryForObject("SELECT setval('refund.refund_items_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM refund.refund_items), 900204))", Long.class);

        log.info("[RefundDevDataLoader] FE test-dataset seeded (4 refunds, 4 refund_items).");
    }
}
