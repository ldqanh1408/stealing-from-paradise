package com.flashsale.orderservice.dto.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO nhận từ payment-service qua Kafka reply (ORDER_REFUNDS_RESPONSE).
 * Dùng cho các query endpoint: GET /orders/{orderId}/refunds, GET /orders/refunds
 */
@Data
public class OrderRefundInfo {

    private Long refundId;
    private String refundCode;
    private Long orderId;
    private String groupRef;
    private String type;
    private String status;
    private BigDecimal amount;
    private BigDecimal adjustAmount;
    private String reason;
    private String refundReasonType;
    private String initiatedBy;
    private String adminNote;
    private String rejectReason;
    private Long reviewedBy;
    private String reviewedAt;
    private String refundRef;
    private String createdAt;
}
