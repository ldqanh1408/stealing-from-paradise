package com.flashsale.paymentservice.service;

import com.flashsale.commonlib.exception.AppException;
import com.flashsale.commonlib.exception.ErrorCode;
import com.flashsale.paymentservice.config.StripeConfig;
import com.flashsale.paymentservice.domain.model.SellerStripeAccount;
import com.flashsale.paymentservice.domain.repository.SellerStripeAccountRepository;
import com.flashsale.paymentservice.dto.response.StripeOnboardingResponse;
import com.flashsale.paymentservice.dto.response.StripeOnboardingStatusResponse;
import com.flashsale.paymentservice.dto.response.AdminSellerStripeAccountsResponse;
import com.flashsale.paymentservice.dto.response.AdminSellerStripeAccountItem;
import com.flashsale.paymentservice.dto.response.AdminSellerStripeSummary;
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
import java.util.List;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeOnboardingService {

    private final SellerStripeAccountRepository sellerStripeAccountRepository;
    private final StripeConfig stripeConfig;

    @Transactional(readOnly = true)
    public AdminSellerStripeAccountsResponse getAllSellersOnboardingStatus() {
        List<SellerStripeAccount> accounts = sellerStripeAccountRepository.findAll();
        
        long total = accounts.size();
        long complete = 0;
        long pending = 0;
        long inProgress = 0;
        long suspended = 0;

        List<AdminSellerStripeAccountItem> items = new ArrayList<>();
        for (SellerStripeAccount acc : accounts) {
            String status = acc.getOnboardingStatus();
            if ("COMPLETE".equals(status)) {
                complete++;
            } else if ("IN_PROGRESS".equals(status)) {
                inProgress++;
            } else if ("SUSPENDED".equals(status)) {
                suspended++;
            } else {
                pending++;
            }

            items.add(AdminSellerStripeAccountItem.builder()
                    .sellerId(acc.getSellerId())
                    .stripeAccountId(acc.getStripeAccountId())
                    .accountStatus(acc.getAccountStatus())
                    .detailsSubmitted(acc.getDetailsSubmitted())
                    .chargesEnabled(acc.getChargesEnabled())
                    .payoutsEnabled(acc.getPayoutsEnabled())
                    .onboardingStatus(status)
                    .createdAt(acc.getCreatedAt())
                    .updatedAt(acc.getUpdatedAt())
                    .build());
        }

        AdminSellerStripeSummary summary = AdminSellerStripeSummary.builder()
                .totalSellers(total)
                .completedSellers(complete)
                .pendingSellers(pending)
                .inProgressSellers(inProgress)
                .suspendedSellers(suspended)
                .build();

        return AdminSellerStripeAccountsResponse.builder()
                .summary(summary)
                .accounts(items)
                .build();
    }

    @Transactional
    public StripeOnboardingResponse startOnboarding(Long sellerId) {
        sellerStripeAccountRepository.findBySellerId(sellerId).ifPresent(existing -> {
            if (Boolean.TRUE.equals(existing.getDetailsSubmitted())) {
                throw new AppException(ErrorCode.ALREADY_EXISTS, "Seller đã có Stripe account hoàn chỉnh (details_submitted = true)");
            }
        });

        try {
            SellerStripeAccount account = sellerStripeAccountRepository.findBySellerId(sellerId)
                    .orElseGet(() -> {
                        try {
                            return createStripeExpressAccount(sellerId);
                        } catch (StripeException e) {
                            log.warn("Failed to create Stripe Express account for seller {}: {}. Falling back to manual onboarding.", sellerId, e.getMessage());
                            return createManualSellerStripeAccount(sellerId);
                        }
                    });

            String onboardingUrl;
            Instant expiresAt = Instant.now().plusSeconds(86400); // 24h

            if (account.getStripeAccountId().startsWith("acct_manual_")) {
                onboardingUrl = stripeConfig.getManualOnboardingFormUrl() + "?sellerId=" + sellerId + "&stripeAccountId=" + account.getStripeAccountId();
                expiresAt = Instant.now().plusSeconds(86400 * 365); // 1 year for manual
                log.info("Using manual onboarding URL for seller {}: {}", sellerId, onboardingUrl);
            } else {
                AccountLink accountLink = createAccountLink(account.getStripeAccountId());
                onboardingUrl = accountLink.getUrl();
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

        if (account.getStripeAccountId().startsWith("acct_manual_")) {
            return StripeOnboardingStatusResponse.builder()
                    .stripeAccountId(account.getStripeAccountId())
                    .accountStatus(account.getAccountStatus())
                    .detailsSubmitted(account.getDetailsSubmitted())
                    .chargesEnabled(account.getChargesEnabled())
                    .payoutsEnabled(account.getPayoutsEnabled())
                    .onboardingStatus(account.getOnboardingStatus() != null ? account.getOnboardingStatus() : "PENDING")
                    .onboardingUrl(account.getOnboardingUrl())
                    .expressDashboardUrl(null)
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

            if (account.getStripeAccountId().startsWith("acct_manual_")) {
                onboardingUrl = stripeConfig.getManualOnboardingFormUrl() + "?sellerId=" + sellerId + "&stripeAccountId=" + account.getStripeAccountId();
                expiresAt = Instant.now().plusSeconds(86400 * 365); // 1 year for manual
                log.info("Using manual refresh onboarding URL for seller {}", sellerId);
            } else {
                try {
                    AccountLink accountLink = createAccountLink(account.getStripeAccountId());
                    onboardingUrl = accountLink.getUrl();
                } catch (StripeException e) {
                    log.warn("Failed to refresh Stripe AccountLink for seller {}: {}. Using return URL.", sellerId, e.getMessage());
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

    private SellerStripeAccount createStripeExpressAccount(Long sellerId) throws StripeException {
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
    }

    private SellerStripeAccount createManualSellerStripeAccount(Long sellerId) {
        String stripeAccountId = "acct_manual_" + sellerId + "_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        String formUrl = stripeConfig.getManualOnboardingFormUrl() + "?sellerId=" + sellerId + "&stripeAccountId=" + stripeAccountId;

        SellerStripeAccount entity = SellerStripeAccount.builder()
                .sellerId(sellerId)
                .stripeAccountId(stripeAccountId)
                .accountStatus("PENDING")
                .chargesEnabled(false)
                .payoutsEnabled(false)
                .detailsSubmitted(false)
                .onboardingUrl(formUrl)
                .onboardingUrlExpiresAt(LocalDateTime.ofInstant(Instant.now().plusSeconds(86400 * 365), ZoneOffset.UTC)) // 1 year for manual
                .build();

        return sellerStripeAccountRepository.save(entity);
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
