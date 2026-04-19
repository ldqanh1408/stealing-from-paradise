package com.flashsale.orderdomain.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class ParentOrderDetailResponse {

    @JsonProperty("parent_order_id")
    private Long parentOrderId;

    @JsonProperty("order_code")
    private String orderCode;

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("total_amt")
    private BigDecimal totalAmt;

    @JsonProperty("loyalty_discount")
    private BigDecimal loyaltyDiscount;

    @JsonProperty("loyalty_points_used")
    private Integer loyaltyPointsUsed;

    @JsonProperty("final_amt")
    private BigDecimal finalAmt;

    @JsonProperty("address_id")
    private Long addressId;

    @JsonProperty("timeout_at")
    private Instant timeoutAt;

    private List<OrderSummaryResponse> orders;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;
}
