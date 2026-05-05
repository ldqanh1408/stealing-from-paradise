package com.flashsale.orderservice.axon.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class OrderReturnedEvent {
    private Long orderId;
    private Long parentOrderId;
    private Long userId;
    private Long sellerId;
    private BigDecimal amount;
    private String returnTrackingNumber;
    private int evidenceCount;
}
