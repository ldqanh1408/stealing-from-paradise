package com.flashsale.orderdomain.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class ReturnToSenderResponse {

    @JsonProperty("order_id")
    private Long orderId;

    @JsonProperty("order_code")
    private String orderCode;

    @JsonProperty("order_status")
    private String orderStatus;

    @JsonProperty("refund_id")
    private Long refundId;

    @JsonProperty("refund_code")
    private String refundCode;

    @JsonProperty("refund_status")
    private String refundStatus;

    @JsonProperty("refund_amount")
    private BigDecimal refundAmount;

    @JsonProperty("return_tracking_number")
    private String returnTrackingNumber;

    @JsonProperty("evidence_count")
    private Integer evidenceCount;

    @JsonProperty("estimated_refund_days")
    private Integer estimatedRefundDays;

    private String message;

    @JsonProperty("seller_notification")
    private NotificationInfo sellerNotification;

    @JsonProperty("buyer_notification")
    private NotificationInfo buyerNotification;

    @JsonProperty("created_at")
    private Instant createdAt;

    @Data
    @Builder
    public static class NotificationInfo {
        private String status;
        private String message;
    }
}
