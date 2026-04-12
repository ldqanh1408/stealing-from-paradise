package com.flashsale.orderdomain.axon.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrderCreatedEvent {
    private String orderId;
    private String buyerId;
    private Long totalAmount;
    private boolean isFlashSale;
}

@Getter
@AllArgsConstructor
class OrderCancelledEvent {
    private String orderId;
    private String reason;
    private String cancelledBy;
}

