package com.flashsale.paymentdomain.axon.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PaymentProcessedEvent {
    private String paymentId;
    private String orderId;
    private Long amount;
    private String vnpayTxnRef;
}

@Getter
@AllArgsConstructor
class RefundIssuedEvent {
    private String paymentId;
    private String refundId;
    private Long refundAmount;
    private String reason;
}

