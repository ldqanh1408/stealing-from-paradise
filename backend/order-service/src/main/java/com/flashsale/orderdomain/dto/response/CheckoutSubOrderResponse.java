package com.flashsale.orderdomain.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class CheckoutSubOrderResponse {

    @JsonProperty("order_id")
    private Long orderId;

    @JsonProperty("order_code")
    private String orderCode;

    @JsonProperty("seller_id")
    private Long sellerId;

    @JsonProperty("seller_name")
    private String sellerName;

    @JsonProperty("total_amt")
    private BigDecimal totalAmt;

    @JsonProperty("final_amt")
    private BigDecimal finalAmt;

    private String status;

    private List<CheckoutOrderItem> items;

    @JsonProperty("created_at")
    private Instant createdAt;
}
