package com.flashsale.paymentservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class StripeOnboardingResponse {

    private String onboardingUrl;
    private String stripeAccountId;
    private Instant expiresAt;
}
