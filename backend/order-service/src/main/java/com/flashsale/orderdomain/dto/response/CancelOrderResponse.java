package com.flashsale.orderdomain.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class CancelOrderResponse {

    @JsonProperty("order_id")
    private Long orderId;

    @JsonProperty("order_code")
    private String orderCode;

    private String status;

    @JsonProperty("cancelled_by")
    private String cancelledBy;

    @JsonProperty("cancel_reason")
    private String cancelReason;

    @JsonProperty("cancelled_at")
    private Instant cancelledAt;
}
