package com.flashsale.paymentdomain.axon.command;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

@Getter
@AllArgsConstructor
public class ProcessPaymentCommand {
    @TargetAggregateIdentifier
    private String paymentId;
    private String orderId;
    private String buyerId;
    private Long amount;
    private String vnpayTxnRef;    // VNPay transaction ref — thay Stripe theo manifest
}

@Getter
@AllArgsConstructor
class IssueRefundCommand {
    @TargetAggregateIdentifier
    private String paymentId;
    private String refundId;
    private Long refundAmount;
    private String reason;  // RTS | ADMIN_APPROVED | BUYER_REQUEST
}

