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
    private final DevDataProperties devDataProperties;

    private static final long[] SELLER_IDS = {1L, 2L, 3L, 4L, 5L};

    private static final long[] PARENT_ORDER_IDS = {1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L};

    private static final BigDecimal[] TX_AMOUNTS = {
            new BigDecimal("250000.00"),
            new BigDecimal("1590000.00"),
            new BigDecimal("899000.00"),
            new BigDecimal("3450000.00"),
            new BigDecimal("459000.00"),
            new BigDecimal("6800000.00"),
            new BigDecimal("1200000.00"),
            new BigDecimal("4200000.00"),
            new BigDecimal("3200000.00"),
            new BigDecimal("8500000.00"),
    };

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
        String[] statuses = {"PAID", "PAID", "PAID", "PAID", "PENDING", "PAID", "PAID", "PAID", "PAID", "PENDING"};
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
}
