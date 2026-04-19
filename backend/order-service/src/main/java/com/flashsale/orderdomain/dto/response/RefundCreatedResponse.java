package com.flashsale.orderdomain.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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

    /** Tổng giá trị đơn hàng gốc */
    @JsonProperty("total_amount")
    private BigDecimal totalAmount;

    /** Số tiền được hoàn (có thể nhỏ hơn total_amount nếu partial) */
    @JsonProperty("refund_amount")
    private BigDecimal refundAmount;

    @JsonProperty("item_count")
    private int itemCount;

    /** Chi tiết từng item được hoàn — order_item_id, quantity, refund_amount, item_reason */
    @JsonProperty("items")
    private List<Map<String, Object>> items;

    @JsonProperty("evidence_images")
    private List<String> evidenceImages;

    @JsonProperty("estimated_days")
    private int estimatedDays;

    @JsonProperty("message")
    private String message;

    @JsonProperty("created_at")
    private Instant createdAt;
}
