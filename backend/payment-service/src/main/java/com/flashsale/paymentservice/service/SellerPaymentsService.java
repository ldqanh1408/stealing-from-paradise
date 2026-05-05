package com.flashsale.paymentservice.service;

import com.flashsale.commonlib.dto.PageResponse;
import com.flashsale.paymentservice.config.StripeConfig;
import com.flashsale.paymentservice.domain.model.SellerStripeAccount;
import com.flashsale.paymentservice.domain.model.SellerTransfer;
import com.flashsale.paymentservice.domain.repository.SellerStripeAccountRepository;
import com.flashsale.paymentservice.domain.repository.SellerTransferRepository;
import com.flashsale.paymentservice.dto.response.SellerBalanceResponse;
import com.flashsale.paymentservice.dto.response.SellerEarningsResponse;
import com.flashsale.paymentservice.dto.response.SellerStripeDashboardResponse;
import com.flashsale.paymentservice.dto.response.SellerEarningsResponse.SellerTransferItem;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.LoginLink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SellerPaymentsService {

    private final SellerTransferRepository sellerTransferRepository;
    private final SellerStripeAccountRepository sellerStripeAccountRepository;
    private final StripeConfig stripeConfig;

    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    @Transactional(readOnly = true)
    public SellerEarningsResponse getSellerEarnings(Long sellerId) {
        List<SellerTransfer> transfers = sellerTransferRepository.findAllBySellerIdOrderByCreatedAtDesc(sellerId);

        java.math.BigDecimal totalEarnings = java.math.BigDecimal.ZERO;
        java.math.BigDecimal availableBalance = java.math.BigDecimal.ZERO;
        java.math.BigDecimal pendingBalance = java.math.BigDecimal.ZERO;

        for (SellerTransfer t : transfers) {
            java.math.BigDecimal fee = t.getTransferAmount() != null
                    ? t.getTransferAmount().multiply(java.math.BigDecimal.valueOf(stripeConfig.getPlatformFeePercentage() / 100.0))
                        .setScale(0, java.math.RoundingMode.HALF_UP)
                    : java.math.BigDecimal.ZERO;
            java.math.BigDecimal net = t.getTransferAmount() != null
                    ? t.getTransferAmount().subtract(fee)
                    : java.math.BigDecimal.ZERO;
            totalEarnings = totalEarnings.add(net);

            if ("SUCCEEDED".equals(t.getStatus())) {
                availableBalance = availableBalance.add(net);
            } else if ("PENDING".equals(t.getStatus())) {
                pendingBalance = pendingBalance.add(net);
            }
        }

        List<SellerTransferItem> items = transfers.stream()
                .map(this::toTransferItem)
                .collect(Collectors.toList());

        return SellerEarningsResponse.builder()
                .totalEarnings(totalEarnings)
                .availableBalance(availableBalance)
                .pendingBalance(pendingBalance)
                .platformFeePercentage(java.math.BigDecimal.valueOf(stripeConfig.getPlatformFeePercentage()))
                .totalOrders((long) transfers.size())
                .transfers(items)
                .build();
    }

    @Transactional(readOnly = true)
    public SellerStripeDashboardResponse getStripeDashboardUrl(Long sellerId) {
        SellerStripeAccount account = sellerStripeAccountRepository.findBySellerId(sellerId)
                .orElseThrow(() -> new com.flashsale.commonlib.exception.AppException(
                        com.flashsale.commonlib.exception.ErrorCode.NOT_FOUND,
                        "Seller chưa kết nối Stripe"));

        if (!Boolean.TRUE.equals(account.getDetailsSubmitted())) {
            throw new com.flashsale.commonlib.exception.AppException(
                    com.flashsale.commonlib.exception.ErrorCode.VALIDATION_FAILED,
                    "Seller chưa hoàn tất onboarding Stripe");
        }

        String dashboardUrl;
        try {
            Account stripeAccount = Account.retrieve(account.getStripeAccountId());
            LoginLink loginLink = LoginLink.createOnAccount(account.getStripeAccountId());
            dashboardUrl = loginLink.getUrl();
        } catch (StripeException e) {
            log.error("Failed to create Stripe dashboard login link for seller {}: {}", sellerId, e.getMessage());
            dashboardUrl = "https://dashboard.stripe.com";
        }

        return SellerStripeDashboardResponse.builder()
                .dashboardUrl(dashboardUrl)
                .stripeAccountId(account.getStripeAccountId())
                .accountStatus(account.getAccountStatus())
                .build();
    }

    private SellerTransferItem toTransferItem(SellerTransfer t) {
        return SellerTransferItem.builder()
                .id(t.getId())
                .orderId(t.getOrderId())
                .transferAmount(t.getTransferAmount())
                .stripeTransferId(t.getStripeTransferId())
                .status(t.getStatus())
                .createdAt(t.getCreatedAt() != null ? t.getCreatedAt().atOffset(ZoneOffset.UTC).format(ISO_FMT) : null)
                .updatedAt(t.getUpdatedAt() != null ? t.getUpdatedAt().atOffset(ZoneOffset.UTC).format(ISO_FMT) : null)
                .build();
    }
}
