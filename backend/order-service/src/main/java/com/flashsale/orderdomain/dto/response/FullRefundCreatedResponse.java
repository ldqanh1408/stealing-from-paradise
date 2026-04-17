package com.flashsale.orderdomain.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class FullRefundCreatedResponse {

    @JsonProperty("group_ref")
    private String groupRef;

    @JsonProperty("parent_order_id")
    private Long parentOrderId;

    @JsonProperty("type")
    private String type;

    @JsonProperty("total_amount")
    private BigDecimal totalAmount;

    @JsonProperty("status")
    private String status;

    @JsonProperty("refunds")
    private List<SubRefundInfo> refunds;

    @JsonProperty("message")
    private String message;

    @JsonProperty("created_at")
    private Instant createdAt;

    @Data
    @Builder
    public static class SubRefundInfo {
        @JsonProperty("order_id")
        private Long orderId;

        @JsonProperty("seller_id")
        private Long sellerId;

        @JsonProperty("amount")
        private BigDecimal amount;

        @JsonProperty("item_count")
        private int itemCount;
    }
}
