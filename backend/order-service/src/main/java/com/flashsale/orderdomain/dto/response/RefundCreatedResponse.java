package com.flashsale.orderdomain.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class RefundCreatedResponse {

    @JsonProperty("group_ref")
    private String groupRef;

    @JsonProperty("order_id")
    private Long orderId;

    @JsonProperty("type")
    private String type;

    @JsonProperty("status")
    private String status;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("item_count")
    private int itemCount;

    @JsonProperty("message")
    private String message;

    @JsonProperty("created_at")
    private Instant createdAt;
}
