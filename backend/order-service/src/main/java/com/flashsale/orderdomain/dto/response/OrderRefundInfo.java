package com.flashsale.orderdomain.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.math.BigDecimal;

/**
 * DTO nhận từ payment-service qua Kafka reply (ORDER_REFUNDS_RESPONSE).
 * Dùng cho các query endpoint: GET /orders/{orderId}/refunds, GET /orders/refunds
 */
@Data
public class OrderRefundInfo {

    @JsonProperty("refund_id")
    private Long refundId;

    @JsonProperty("refund_code")
    private String refundCode;

    @JsonProperty("order_id")
    private Long orderId;

    @JsonProperty("group_ref")
    private String groupRef;

    @JsonProperty("type")
    private String type;

    @JsonProperty("status")
    private String status;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("adjust_amount")
    private BigDecimal adjustAmount;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("refund_reason_type")
    private String refundReasonType;

    @JsonProperty("initiated_by")
    private String initiatedBy;

    @JsonProperty("admin_note")
    private String adminNote;

    @JsonProperty("reject_reason")
    private String rejectReason;

    @JsonProperty("reviewed_by")
    private Long reviewedBy;

    @JsonProperty("reviewed_at")
    private String reviewedAt;

    @JsonProperty("refund_ref")
    private String refundRef;

    @JsonProperty("created_at")
    private String createdAt;
}
