package com.flashsale.paymentservice.service;

import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.paymentservice.config.StripeConfig;
import com.flashsale.paymentservice.domain.model.SellerStripeAccount;
import com.flashsale.paymentservice.domain.repository.SellerStripeAccountRepository;
import com.flashsale.paymentservice.dto.response.StripeOnboardingResponse;
import com.flashsale.paymentservice.dto.response.StripeOnboardingStatusResponse;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.model.LoginLink;
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

            String onboardingUrl;
            Instant expiresAt = Instant.now().plusSeconds(86400); // 24h

            if (account.getStripeAccountId().startsWith("acct_mock_")) {
                onboardingUrl = stripeConfig.getOnboardingReturnUrl();
                log.info("Using mock onboarding URL for seller {}: {}", sellerId, onboardingUrl);
            } else {
                try {
                    AccountLink accountLink = createAccountLink(account.getStripeAccountId());
                    onboardingUrl = accountLink.getUrl();
                } catch (StripeException e) {
                    log.warn("Failed to create Stripe AccountLink for seller {}: {}. Falling back to mock URL.", sellerId, e.getMessage());
                    onboardingUrl = stripeConfig.getOnboardingReturnUrl();
                }
            }

            account.setOnboardingUrl(onboardingUrl);
            account.setOnboardingUrlExpiresAt(LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
            sellerStripeAccountRepository.save(account);

            log.info("Stripe onboarding started for seller {}: account={}", sellerId, account.getStripeAccountId());

            return StripeOnboardingResponse.builder()
                    .onboardingUrl(onboardingUrl)
                    .stripeAccountId(account.getStripeAccountId())
                    .expiresAt(expiresAt)
                    .build();

        } catch (Exception e) {
            log.error("Error during onboarding start for seller {}: {}", sellerId, e.getMessage());
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Stripe API error: " + e.getMessage());
        }
    }

    @Transactional
    public StripeOnboardingStatusResponse getOnboardingStatus(Long sellerId) {
        SellerStripeAccount account = sellerStripeAccountRepository.findBySellerId(sellerId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Seller chưa bắt đầu onboarding Stripe"));

        if (account.getStripeAccountId().startsWith("acct_mock_")) {
            // Auto-complete the mock onboarding if it is not completed yet
            if (!Boolean.TRUE.equals(account.getDetailsSubmitted())) {
                account.setDetailsSubmitted(true);
                account.setChargesEnabled(true);
                account.setPayoutsEnabled(true);
                account.setAccountStatus("ACTIVE");
                account.setOnboardingUrl(null);
                account.setOnboardingUrlExpiresAt(null);
                sellerStripeAccountRepository.save(account);
                log.info("Mock Stripe onboarding completed for seller {}", sellerId);
            }
            return StripeOnboardingStatusResponse.builder()
                    .stripeAccountId(account.getStripeAccountId())
                    .accountStatus(account.getAccountStatus())
                    .detailsSubmitted(account.getDetailsSubmitted())
                    .chargesEnabled(account.getChargesEnabled())
                    .payoutsEnabled(account.getPayoutsEnabled())
                    .onboardingStatus("COMPLETE")
                    .onboardingUrl(null)
                    .expressDashboardUrl("https://connect.stripe.com/express/" + account.getStripeAccountId())
                    .build();
        }

        // Always query Stripe for the latest status. This is the primary sync mechanism.
        // Webhook (account.updated) is secondary — it may arrive late or not at all in dev.
        // Querying Stripe on every status check ensures the frontend always sees accurate data.
        String derivedStatus = null;
        try {
            Account stripeAccount = Account.retrieve(account.getStripeAccountId());
            boolean needsUpdate =
                    !java.lang.Boolean.TRUE.equals(stripeAccount.getDetailsSubmitted())
                            != !java.lang.Boolean.TRUE.equals(account.getDetailsSubmitted())
                    || !java.lang.Boolean.TRUE.equals(stripeAccount.getChargesEnabled())
                            != !java.lang.Boolean.TRUE.equals(account.getChargesEnabled())
                    || !java.lang.Boolean.TRUE.equals(stripeAccount.getPayoutsEnabled())
                            != !java.lang.Boolean.TRUE.equals(account.getPayoutsEnabled());

            if (needsUpdate) {
                account.setDetailsSubmitted(java.lang.Boolean.TRUE.equals(stripeAccount.getDetailsSubmitted()));
                account.setChargesEnabled(java.lang.Boolean.TRUE.equals(stripeAccount.getChargesEnabled()));
                account.setPayoutsEnabled(java.lang.Boolean.TRUE.equals(stripeAccount.getPayoutsEnabled()));

                if (java.lang.Boolean.TRUE.equals(stripeAccount.getDetailsSubmitted())) {
                    account.setAccountStatus("ACTIVE");
                    account.setOnboardingUrl(null);
                    account.setOnboardingUrlExpiresAt(null);
                } else if ("restricted".equals(stripeAccount.getRequirements().getDisabledReason())) {
                    account.setAccountStatus("SUSPENDED");
                }
                sellerStripeAccountRepository.save(account);
                log.info("Stripe status synced for seller {}: details_submitted={}, charges_enabled={}, payouts_enabled={}",
                        sellerId, account.getDetailsSubmitted(), account.getChargesEnabled(), account.getPayoutsEnabled());
            }

            // Derive onboarding status directly from Stripe response, not from potentially-stale DB fields.
            // Stripe flow: PENDING → (details_submitted=true, charges=false) → IN_PROGRESS → (charges=true) → COMPLETE
            derivedStatus = deriveOnboardingStatus(stripeAccount);

            // Get Express Dashboard URL for identity verification.
            // For Express accounts, construct directly: https://connect.stripe.com/express/{account_id}
            // This is the same URL format as the verify link seller used.
            String expressDashboardUrl = "https://connect.stripe.com/express/" + stripeAccount.getId();

            // Return the URL so frontend can display it as a "Continue Verification" link
            return StripeOnboardingStatusResponse.builder()
                    .stripeAccountId(account.getStripeAccountId())
                    .accountStatus(account.getAccountStatus())
                    .detailsSubmitted(account.getDetailsSubmitted())
                    .chargesEnabled(account.getChargesEnabled())
                    .payoutsEnabled(account.getPayoutsEnabled())
                    .onboardingStatus(derivedStatus)
                    .onboardingUrl(java.lang.Boolean.TRUE.equals(account.getDetailsSubmitted()) ? null : account.getOnboardingUrl())
                    .expressDashboardUrl(expressDashboardUrl)
                    .build();
        } catch (StripeException e) {
            log.warn("Failed to query Stripe status for seller {}: {}. Using DB state.", sellerId, e.getMessage());
            return StripeOnboardingStatusResponse.builder()
                    .stripeAccountId(account.getStripeAccountId())
                    .accountStatus(account.getAccountStatus())
                    .detailsSubmitted(account.getDetailsSubmitted())
                    .chargesEnabled(account.getChargesEnabled())
                    .payoutsEnabled(account.getPayoutsEnabled())
                    .onboardingStatus(account.getOnboardingStatus())
                    .onboardingUrl(account.getOnboardingUrl())
                    .expressDashboardUrl(account.getExpressDashboardUrl())
                    .build();
        }
    }

    private String deriveOnboardingStatus(Account stripeAccount) {
        boolean detailsSubmitted = java.lang.Boolean.TRUE.equals(stripeAccount.getDetailsSubmitted());
        boolean chargesEnabled   = java.lang.Boolean.TRUE.equals(stripeAccount.getChargesEnabled());
        boolean payoutsEnabled   = java.lang.Boolean.TRUE.equals(stripeAccount.getPayoutsEnabled());

        if ("restricted".equals(stripeAccount.getRequirements().getDisabledReason())) {
            return "SUSPENDED";
        }
        if (detailsSubmitted && (chargesEnabled || payoutsEnabled)) {
            return "COMPLETE";
        }
        if (detailsSubmitted) {
            // Identity verified but Stripe still reviewing capabilities — seller is mid-process
            return "IN_PROGRESS";
        }
        return "PENDING";
    }

    @Transactional
    public StripeOnboardingResponse refreshOnboardingLink(Long sellerId) {
        SellerStripeAccount account = sellerStripeAccountRepository.findBySellerId(sellerId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Seller chưa bắt đầu onboarding Stripe"));

        if (Boolean.TRUE.equals(account.getDetailsSubmitted())) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "Seller đã hoàn tất KYC, không cần refresh link");
        }

        try {
            String onboardingUrl;
            Instant expiresAt = Instant.now().plusSeconds(86400);

            if (account.getStripeAccountId().startsWith("acct_mock_")) {
                onboardingUrl = stripeConfig.getOnboardingReturnUrl();
                log.info("Using mock refresh onboarding URL for seller {}", sellerId);
            } else {
                try {
                    AccountLink accountLink = createAccountLink(account.getStripeAccountId());
                    onboardingUrl = accountLink.getUrl();
                } catch (StripeException e) {
                    log.warn("Failed to refresh Stripe AccountLink for seller {}: {}. Falling back to mock URL.", sellerId, e.getMessage());
                    onboardingUrl = stripeConfig.getOnboardingReturnUrl();
                }
            }

            account.setOnboardingUrl(onboardingUrl);
            account.setOnboardingUrlExpiresAt(LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
            sellerStripeAccountRepository.save(account);

            log.info("Stripe onboarding link refreshed for seller {}", sellerId);

            return StripeOnboardingResponse.builder()
                    .onboardingUrl(onboardingUrl)
                    .stripeAccountId(account.getStripeAccountId())
                    .expiresAt(expiresAt)
                    .build();

        } catch (Exception e) {
            log.error("Error refreshing link for seller {}: {}", sellerId, e.getMessage());
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Stripe API error: " + e.getMessage());
        }
    }

    private SellerStripeAccount createStripeExpressAccount(Long sellerId) {
        try {
            AccountCreateParams params = AccountCreateParams.builder()
            .setType(AccountCreateParams.Type.EXPRESS)
            // Set quốc gia (Nên set US để dễ test Onboarding nhất)
            .setCountry("US") 
            // Yêu cầu các quyền (Capabilities) ngay lúc tạo
            .setCapabilities(
                AccountCreateParams.Capabilities.builder()
                    .setCardPayments(
                        AccountCreateParams.Capabilities.CardPayments.builder()
                            .setRequested(true)
                            .build()
                    )
                    .setTransfers(
                        AccountCreateParams.Capabilities.Transfers.builder()
                            .setRequested(true)
                            .build()
                    )
                    .build()
            )
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
            log.warn("Failed to create Stripe Express account for seller {} via Stripe API: {}. Falling back to MOCK mode.", sellerId, e.getMessage());
            
            SellerStripeAccount entity = SellerStripeAccount.builder()
                    .sellerId(sellerId)
                    .stripeAccountId("acct_mock_" + sellerId + "_" + java.util.UUID.randomUUID().toString().substring(0, 8))
                    .accountStatus("PENDING")
                    .chargesEnabled(false)
                    .payoutsEnabled(false)
                    .detailsSubmitted(false)
                    .build();

            return sellerStripeAccountRepository.save(entity);
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
