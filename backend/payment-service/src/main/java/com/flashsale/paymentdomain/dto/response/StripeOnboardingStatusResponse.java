package com.flashsale.paymentdomain.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StripeOnboardingStatusResponse {

    @JsonProperty("stripe_account_id")
    private String stripeAccountId;

    @JsonProperty("account_status")
    private String accountStatus;

    @JsonProperty("details_submitted")
    private Boolean detailsSubmitted;

    @JsonProperty("charges_enabled")
    private Boolean chargesEnabled;

    @JsonProperty("payouts_enabled")
    private Boolean payoutsEnabled;

    @JsonProperty("onboarding_status")
    private String onboardingStatus;

    @JsonProperty("onboarding_url")
    private String onboardingUrl;
}
