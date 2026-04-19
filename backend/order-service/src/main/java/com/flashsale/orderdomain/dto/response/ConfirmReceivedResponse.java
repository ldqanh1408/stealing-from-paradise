package com.flashsale.orderdomain.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ConfirmReceivedResponse {

    @JsonProperty("order_id")
    private Long orderId;

    @JsonProperty("order_code")
    private String orderCode;

    private String status;

    @JsonProperty("delivered_at")
    private Instant deliveredAt;

    @JsonProperty("loyalty_points_confirmed")
    private Integer loyaltyPointsConfirmed;

    @JsonProperty("seller_trust_score_delta")
    private Integer sellerTrustScoreDelta;
}
