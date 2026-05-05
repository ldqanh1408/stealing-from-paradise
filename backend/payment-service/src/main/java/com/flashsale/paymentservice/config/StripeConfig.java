package com.flashsale.paymentservice.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class StripeConfig {

    @Value("${stripe.secret-key}")
    private String secretKey;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @Value("${stripe.platform-fee-percentage:5.0}")
    private double platformFeePercentage;

    @Value("${stripe.onboarding-return-url}")
    private String onboardingReturnUrl;

    @Value("${stripe.onboarding-refresh-url}")
    private String onboardingRefreshUrl;

    @Value("${stripe.default-country:US}")
    private String defaultCountry;

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
        log.info("Stripe SDK initialized. Platform fee: {}%", platformFeePercentage);
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public double getPlatformFeePercentage() {
        return platformFeePercentage;
    }

    public String getOnboardingReturnUrl() {
        return onboardingReturnUrl;
    }

    public String getOnboardingRefreshUrl() {
        return onboardingRefreshUrl;
    }

    public String getDefaultCountry() {
        return defaultCountry;
    }
}
