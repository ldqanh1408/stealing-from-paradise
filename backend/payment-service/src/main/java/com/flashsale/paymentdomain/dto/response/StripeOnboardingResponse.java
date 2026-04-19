package com.flashsale.paymentdomain.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class StripeOnboardingResponse {

    @JsonProperty("onboarding_url")
    private String onboardingUrl;

    @JsonProperty("stripe_account_id")
    private String stripeAccountId;

    @JsonProperty("expires_at")
    private Instant expiresAt;
}
