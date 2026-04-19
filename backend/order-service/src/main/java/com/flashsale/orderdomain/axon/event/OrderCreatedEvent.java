package com.flashsale.orderdomain.axon.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class OrderCreatedEvent {
    private Long orderId;
    private Long parentOrderId;
    private Long userId;
    private Long sellerId;
    private String sellerName;
    private String orderCode;
    private BigDecimal totalAmount;
    private int loyaltyPointsUsed;
    private boolean isFlashSale;
    private LocalDateTime timeoutAt;
}
