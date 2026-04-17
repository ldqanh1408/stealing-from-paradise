package com.flashsale.paymentdomain.service;

import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.paymentdomain.config.StripeConfig;
import com.flashsale.paymentdomain.domain.model.SellerStripeAccount;
import com.flashsale.paymentdomain.domain.repository.SellerStripeAccountRepository;
import com.flashsale.paymentdomain.dto.response.StripeOnboardingResponse;
import com.flashsale.paymentdomain.dto.response.StripeOnboardingStatusResponse;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeOnboardingService {

    private final SellerStripeAccountRepository sellerStripeAccountRepository;
    private final StripeConfig stripeConfig;

    @Transactional
    public StripeOnboardingResponse startOnboarding(Long sellerId) {
        sellerStripeAccountRepository.findBySellerId(sellerId).ifPresent(existing -> {
            if (Boolean.TRUE.equals(existing.getDetailsSubmitted())) {
                throw new AppException(ErrorCode.ALREADY_EXISTS, "Seller đã có Stripe account hoàn chỉnh (details_submitted = true)");
            }
        });

        try {
            SellerStripeAccount account = sellerStripeAccountRepository.findBySellerId(sellerId)
                    .orElseGet(() -> createStripeExpressAccount(sellerId));

            AccountLink accountLink = createAccountLink(account.getStripeAccountId());
            Instant expiresAt = Instant.now().plusSeconds(86400); // 24h

            account.setOnboardingUrl(accountLink.getUrl());
            account.setOnboardingUrlExpiresAt(LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
            sellerStripeAccountRepository.save(account);

            log.info("Stripe onboarding started for seller {}: account={}", sellerId, account.getStripeAccountId());

            return StripeOnboardingResponse.builder()
                    .onboardingUrl(accountLink.getUrl())
                    .stripeAccountId(account.getStripeAccountId())
                    .expiresAt(expiresAt)
                    .build();

        } catch (StripeException e) {
            log.error("Stripe API error during onboarding start for seller {}: {}", sellerId, e.getMessage());
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Stripe API error: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public StripeOnboardingStatusResponse getOnboardingStatus(Long sellerId) {
        SellerStripeAccount account = sellerStripeAccountRepository.findBySellerId(sellerId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Seller chưa bắt đầu onboarding Stripe"));

        return StripeOnboardingStatusResponse.builder()
                .stripeAccountId(account.getStripeAccountId())
                .accountStatus(account.getAccountStatus())
                .detailsSubmitted(account.getDetailsSubmitted())
                .chargesEnabled(account.getChargesEnabled())
                .payoutsEnabled(account.getPayoutsEnabled())
                .onboardingStatus(account.getOnboardingStatus())
                .onboardingUrl(account.getDetailsSubmitted() ? null : account.getOnboardingUrl())
                .build();
    }

    @Transactional
    public StripeOnboardingResponse refreshOnboardingLink(Long sellerId) {
        SellerStripeAccount account = sellerStripeAccountRepository.findBySellerId(sellerId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Seller chưa bắt đầu onboarding Stripe"));

        if (Boolean.TRUE.equals(account.getDetailsSubmitted())) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "Seller đã hoàn tất KYC, không cần refresh link");
        }

        try {
            AccountLink accountLink = createAccountLink(account.getStripeAccountId());
            Instant expiresAt = Instant.now().plusSeconds(86400);

            account.setOnboardingUrl(accountLink.getUrl());
            account.setOnboardingUrlExpiresAt(LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
            sellerStripeAccountRepository.save(account);

            log.info("Stripe onboarding link refreshed for seller {}", sellerId);

            return StripeOnboardingResponse.builder()
                    .onboardingUrl(accountLink.getUrl())
                    .stripeAccountId(account.getStripeAccountId())
                    .expiresAt(expiresAt)
                    .build();

        } catch (StripeException e) {
            log.error("Stripe API error refreshing link for seller {}: {}", sellerId, e.getMessage());
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Stripe API error: " + e.getMessage());
        }
    }

    private SellerStripeAccount createStripeExpressAccount(Long sellerId) {
        try {
            AccountCreateParams params = AccountCreateParams.builder()
                    .setType(AccountCreateParams.Type.EXPRESS)
                    .setCountry("VN")
                    .build();

            Account stripeAccount = Account.create(params);

            SellerStripeAccount entity = SellerStripeAccount.builder()
                    .sellerId(sellerId)
                    .stripeAccountId(stripeAccount.getId())
                    .accountStatus("PENDING")
                    .chargesEnabled(false)
                    .payoutsEnabled(false)
                    .detailsSubmitted(false)
                    .build();

            return sellerStripeAccountRepository.save(entity);

        } catch (StripeException e) {
            log.error("Failed to create Stripe Express account for seller {}: {}", sellerId, e.getMessage());
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Cannot create Stripe account: " + e.getMessage());
        }
    }

    private AccountLink createAccountLink(String stripeAccountId) throws StripeException {
        AccountLinkCreateParams params = AccountLinkCreateParams.builder()
                .setAccount(stripeAccountId)
                .setRefreshUrl(stripeConfig.getOnboardingRefreshUrl())
                .setReturnUrl(stripeConfig.getOnboardingReturnUrl())
                .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                .build();

        return AccountLink.create(params);
    }
}
