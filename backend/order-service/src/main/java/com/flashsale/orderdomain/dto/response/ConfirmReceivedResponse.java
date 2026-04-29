package com.flashsale.orderdomain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ConfirmReceivedResponse {

    private Long orderId;
    private String orderCode;
    private String status;
    private Instant deliveredAt;
    private Integer loyaltyPointsConfirmed;
    private Integer sellerTrustScoreDelta;
}
