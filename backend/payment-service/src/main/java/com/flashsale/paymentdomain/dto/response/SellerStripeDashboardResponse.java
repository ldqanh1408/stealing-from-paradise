package com.flashsale.paymentdomain.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SellerStripeDashboardResponse {

    @JsonProperty("dashboard_url")
    private String dashboardUrl;

    @JsonProperty("stripe_account_id")
    private String stripeAccountId;

    @JsonProperty("account_status")
    private String accountStatus;
}
