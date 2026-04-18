package com.flashsale.orderdomain.axon.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ParentOrderCheckoutCreatedEvent {
    private Long parentOrderId;
    private Long userId;
    private BigDecimal totalAmount;
    private LocalDateTime timeoutAt;
}

