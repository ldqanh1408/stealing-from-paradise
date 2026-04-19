package com.flashsale.paymentdomain.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class RefundDetailResponse {

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

    @JsonProperty("initiated_by")
    private String initiatedBy;

    @JsonProperty("refund_reason_type")
    private String refundReasonType;

    @JsonProperty("evidence_images")
    private List<String> evidenceImages;

    @JsonProperty("admin_note")
    private String adminNote;

    @JsonProperty("reject_reason")
    private String rejectReason;

    @JsonProperty("caused_by")
    private String causedBy;

    @JsonProperty("tracking_number")
    private String trackingNumber;

    @JsonProperty("return_evidence")
    private List<AdminRefundApproveResponse.ReturnEvidence> returnEvidence;

    @JsonProperty("reviewed_by")
    private Long reviewedBy;

    @JsonProperty("reviewed_at")
    private Instant reviewedAt;

    @JsonProperty("stripe_refund_id")
    private String stripeRefundId;

    @JsonProperty("items")
    private List<RefundItemInfo> items;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    @Data
    @Builder
    public static class RefundItemInfo {
        @JsonProperty("item_id")
        private Long itemId;

        @JsonProperty("quantity")
        private Integer quantity;

        @JsonProperty("refund_amount")
        private BigDecimal refundAmount;

        @JsonProperty("item_reason")
        private String itemReason;

        @JsonProperty("status")
        private String status;

        @JsonProperty("return_tracking_number")
        private String returnTrackingNumber;

        @JsonProperty("returned_at")
        private Instant returnedAt;
    }
}
