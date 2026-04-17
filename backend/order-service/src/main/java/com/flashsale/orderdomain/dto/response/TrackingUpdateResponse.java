package com.flashsale.orderdomain.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class TrackingUpdateResponse {

    @JsonProperty("order_id")
    private Long orderId;

    @JsonProperty("order_code")
    private String orderCode;

    private String status;

    @JsonProperty("tracking_number")
    private String trackingNumber;

    private String carrier;

    @JsonProperty("shipping_deadline")
    private Instant shippingDeadline;

    @JsonProperty("updated_at")
    private Instant updatedAt;
}
