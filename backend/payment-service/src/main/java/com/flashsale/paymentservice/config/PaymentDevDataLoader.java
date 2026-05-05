package com.flashsale.paymentservice.config;

import com.flashsale.commonlib.config.DevDataProperties;
import com.flashsale.paymentservice.domain.model.*;
import com.flashsale.paymentservice.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "dev-data.enabled", havingValue = "true", matchIfMissing = false)
public class PaymentDevDataLoader implements CommandLineRunner {

    private final SellerStripeAccountRepository sellerStripeAccountRepository;
    private final TransactionRepository transactionRepository;
    private final SellerTransferRepository sellerTransferRepository;
    private final RefundRepository refundRepository;
    private final RefundItemRepository refundItemRepository;
    private final DevDataProperties devDataProperties;

    // ------------------------------------------------------------------ //
    //  ID RANGES — aligned with identity-service and order-service
    //  Identity:  users 1-10, sellers 1-5
    //  Order:     parent_orders 1-50, orders 1-100
    //  Payment:   transactions 1-50, refunds 1-20
    // ------------------------------------------------------------------ //

    private static final long[] SELLER_IDS = {1L, 2L, 3L, 4L, 5L};
    private static final long[] USER_IDS   = {1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L};

    // Parent order IDs that match order-service seed (1-10)
    private static final long[] PARENT_ORDER_IDS = {1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L};

    // Transaction amounts (VND) — 10 orders
    private static final BigDecimal[] TX_AMOUNTS = {
            new BigDecimal("250000.00"),   // order 1
            new BigDecimal("1590000.00"),  // order 2
            new BigDecimal("899000.00"),   // order 3
            new BigDecimal("3450000.00"),  // order 4
            new BigDecimal("459000.00"),   // order 5 (PENDING)
            new BigDecimal("6800000.00"),  // order 6 (new)
            new BigDecimal("1200000.00"),  // order 7 (new)
            new BigDecimal("4200000.00"),  // order 8 (new)
            new BigDecimal("3200000.00"),  // order 9 (new)
            new BigDecimal("8500000.00"),  // order 10 (PENDING, new)
    };

    // Stripe test Connect account IDs (5 sellers)
    private static final String[] STRIPE_ACCOUNT_IDS = {
            "acct_test_SELLER001_AABBCC",
            "acct_test_SELLER002_DDEEFF",
            "acct_test_SELLER003_GGHHII",
            "acct_test_SELLER004_JJKKLL",
            "acct_test_SELLER005_MMNOPP",
    };

    @Override
    @Transactional
    public void run(String... args) {
        log.info("[PaymentDevDataLoader] Starting dev data seed for payment-service...");

        if (devDataProperties.isReset()) {
            log.warn("[PaymentDevDataLoader] RESET=true — wiping all payment data...");
            refundItemRepository.deleteAll();
            refundRepository.deleteAll();
            sellerTransferRepository.deleteAll();
            transactionRepository.deleteAll();
            sellerStripeAccountRepository.deleteAll();
            log.info("[PaymentDevDataLoader] All payment data wiped.");
        } else if (sellerStripeAccountRepository.count() > 0) {
            log.info("[PaymentDevDataLoader] Data already exists, skipping. Set dev-data.reset=true to reload.");
            return;
        }

        seedSellerStripeAccounts();
        seedTransactionsAndTransfers();
        seedRefunds();

        log.info("[PaymentDevDataLoader] Dev data seed complete.");
    }

    private void seedSellerStripeAccounts() {
        for (int i = 0; i < SELLER_IDS.length; i++) {
            SellerStripeAccount account = SellerStripeAccount.builder()
                    .sellerId(SELLER_IDS[i])
                    .stripeAccountId(STRIPE_ACCOUNT_IDS[i])
                    .accountStatus("ACTIVE")
                    .chargesEnabled(true)
                    .payoutsEnabled(true)
                    .detailsSubmitted(true)
                    .onboardingUrl(null)
                    .build();
            sellerStripeAccountRepository.save(account);
        }
        log.info("[PaymentDevDataLoader] Seeded {} seller stripe accounts", SELLER_IDS.length);
    }

    private void seedTransactionsAndTransfers() {
        // Order statuses: 1-4 COMPLETED, 5 PENDING, 6-9 PAID, 10 PENDING
        String[] statuses = {"PAID", "PAID", "PAID", "PAID", "PENDING", "PAID", "PAID", "PAID", "PAID", "PENDING"};
        long[] userIds   = {1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L};
        long[] sellerIds = {1L, 2L, 3L, 1L, 2L, 4L, 5L, 1L, 3L, 4L};

        for (int i = 0; i < PARENT_ORDER_IDS.length; i++) {
            String status = statuses[i];
            BigDecimal amount = TX_AMOUNTS[i];
            LocalDateTime paidAt = "PAID".equals(status) ? LocalDateTime.now().minusDays(7 - i) : null;
            BigDecimal transferAmount = "PAID".equals(status) ? amount.multiply(new BigDecimal("0.95")) : null;

            Transaction tx = Transaction.builder()
                    .parentOrderId(PARENT_ORDER_IDS[i])
                    .amount(amount)
                    .transRef("TX" + String.format("%06d", i + 1))
                    .stripeConnectMode("DESTINATION")
                    .applicationFeeAmount(amount.multiply(new BigDecimal("0.05")))
                    .status(status)
                    .payAt(paidAt)
                    .build();
            tx = transactionRepository.save(tx);

            if (transferAmount != null) {
                SellerTransfer transfer = SellerTransfer.builder()
                        .orderId(PARENT_ORDER_IDS[i])
                        .sellerId(sellerIds[i])
                        .transferAmount(amount)
                        .stripeTransferId("tr_test_" + UUID.randomUUID().toString().substring(0, 16).toUpperCase())
                        .status("COMPLETED")
                        .build();
                sellerTransferRepository.save(transfer);
            }
        }

        log.info("[PaymentDevDataLoader] Seeded {} transactions + transfers", PARENT_ORDER_IDS.length);
    }

    private void seedRefunds() {
        // Refund 1: Full refund on order 1 (COMPLETED refund)
        Transaction tx1 = transactionRepository.findByParentOrderId(1L).orElse(null);
        if (tx1 != null) {
            Refund refund1 = Refund.builder()
                    .transactionId(tx1.getId())
                    .orderId(1L)
                    .groupRef(UUID.randomUUID())
                    .type("FULL")
                    .initiatedBy("BUYER")
                    .refundReasonType("CHANGE_OF_MIND")
                    .amount(tx1.getAmount())
                    .reason("Không còn nhu cầu mua nữa")
                    .status("COMPLETED")
                    .refundRef("RFND-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .reviewedBy(1L)
                    .reviewedAt(LocalDateTime.now().minusDays(3))
                    .rawResponse(Map.of(
                            "id", "re_test_refund1",
                            "object", "refund",
                            "status", "succeeded"
                    ))
                    .build();
            refund1 = refundRepository.save(refund1);
        }

        // Refund 2: Partial refund on order 2 (PENDING admin review)
        Transaction tx2 = transactionRepository.findByParentOrderId(2L).orElse(null);
        if (tx2 != null) {
            Refund refund2 = Refund.builder()
                    .transactionId(tx2.getId())
                    .orderId(2L)
                    .groupRef(UUID.randomUUID())
                    .type("PARTIAL")
                    .initiatedBy("BUYER")
                    .refundReasonType("DEFECTIVE")
                    .amount(new BigDecimal("500000.00"))
                    .reason("Sản phẩm bị lỗi, xin hoàn 500k")
                    .status("PENDING")
                    .evidenceImages(Arrays.asList(
                            "https://picsum.photos/400/300?random=1",
                            "https://picsum.photos/400/300?random=2"
                    ))
                    .build();
            refund2 = refundRepository.save(refund2);

            RefundItem item1 = RefundItem.builder()
                    .refundId(refund2.getId())
                    .itemId(1L)
                    .quantity(1)
                    .refundAmount(new BigDecimal("300000.00"))
                    .itemReason("Sản phẩm bị trầy xước")
                    .status("PENDING")
                    .build();
            RefundItem item2 = RefundItem.builder()
                    .refundId(refund2.getId())
                    .itemId(2L)
                    .quantity(1)
                    .refundAmount(new BigDecimal("200000.00"))
                    .itemReason("Thiếu phụ kiện đi kèm")
                    .status("PENDING")
                    .build();
            refundItemRepository.saveAll(Arrays.asList(item1, item2));
        }

        // Refund 3: REJECTED refund on order 3
        Transaction tx3 = transactionRepository.findByParentOrderId(3L).orElse(null);
        if (tx3 != null) {
            Refund refund3 = Refund.builder()
                    .transactionId(tx3.getId())
                    .orderId(3L)
                    .groupRef(UUID.randomUUID())
                    .type("FULL")
                    .initiatedBy("BUYER")
                    .refundReasonType("WRONG_ITEM")
                    .amount(tx3.getAmount())
                    .reason("Nhận được sai sản phẩm")
                    .status("REJECTED")
                    .rejectReason("Hình ảnh không rõ ràng, không đủ bằng chứng")
                    .adminNote("Yêu cầu khách cung cấp video unboxing")
                    .reviewedBy(1L)
                    .reviewedAt(LocalDateTime.now().minusDays(2))
                    .build();
            refundRepository.save(refund3);
        }

        // Refund 4: RTS (Return-to-Sender) in progress on order 4
        Transaction tx4 = transactionRepository.findByParentOrderId(4L).orElse(null);
        if (tx4 != null) {
            Refund refund4 = Refund.builder()
                    .transactionId(tx4.getId())
                    .orderId(4L)
                    .groupRef(UUID.randomUUID())
                    .type("FULL")
                    .initiatedBy("BUYER")
                    .refundReasonType("NOT_RECEIVED")
                    .amount(tx4.getAmount())
                    .reason("Đơn hàng bị lost trong quá trình vận chuyển")
                    .status("RTS_COMPLETED")
                    .reviewedBy(1L)
                    .reviewedAt(LocalDateTime.now().minusDays(1))
                    .rawResponse(Map.of(
                            "id", "re_test_rts",
                            "object", "refund",
                            "status", "succeeded"
                    ))
                    .build();
            refund4 = refundRepository.save(refund4);

            RefundItem item = RefundItem.builder()
                    .refundId(refund4.getId())
                    .itemId(1L)
                    .quantity(1)
                    .refundAmount(tx4.getAmount())
                    .status("RETURNED")
                    .returnTrackingNumber("VNPOST" + new Random().nextInt(9000000) + 1000000)
                    .returnEvidenceImages(List.of("https://picsum.photos/400/300?random=10"))
                    .returnedAt(LocalDateTime.now().minusHours(6))
                    .build();
            refundItemRepository.save(item);
        }

        log.info("[PaymentDevDataLoader] Seeded refunds");
    }
}
