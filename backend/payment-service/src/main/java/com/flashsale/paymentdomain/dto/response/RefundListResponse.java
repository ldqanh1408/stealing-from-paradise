package com.flashsale.paymentdomain.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class RefundListResponse {

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

    @JsonProperty("initiated_by")
    private String initiatedBy;

    @JsonProperty("refund_reason_type")
    private String refundReasonType;

    @JsonProperty("admin_note")
    private String adminNote;

    @JsonProperty("reject_reason")
    private String rejectReason;

    @JsonProperty("reviewed_by")
    private Long reviewedBy;

    @JsonProperty("reviewed_at")
    private Instant reviewedAt;

    @JsonProperty("refund_ref")
    private String refundRef;

    @JsonProperty("created_at")
    private Instant createdAt;
}
