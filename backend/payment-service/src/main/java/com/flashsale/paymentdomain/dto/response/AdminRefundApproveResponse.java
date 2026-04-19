package com.flashsale.paymentdomain.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class AdminRefundApproveResponse {

    @JsonProperty("refund_id")
    private Long refundId;

    @JsonProperty("refund_code")
    private String refundCode;

    @JsonProperty("status")
    private String status;

    @JsonProperty("type")
    private String type;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("adjust_amount")
    private BigDecimal adjustAmount;

    @JsonProperty("tracking_number")
    private String trackingNumber;

    @JsonProperty("return_evidence")
    private List<ReturnEvidence> returnEvidence;

    @JsonProperty("reviewed_by")
    private Long reviewedBy;

    @JsonProperty("admin_note")
    private String adminNote;

    @JsonProperty("reviewed_at")
    private Instant reviewedAt;

    @JsonProperty("stripe_refund_id")
    private String stripeRefundId;

    @Data
    @Builder
    public static class ReturnEvidence {
        @JsonProperty("type")
        private String type;

        @JsonProperty("tracking_number")
        private String trackingNumber;

        @JsonProperty("recorded_at")
        private Instant recordedAt;
    }
}
