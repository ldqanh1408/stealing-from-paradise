package com.flashsale.orderservice.axon.command;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

@Getter
@AllArgsConstructor
public class CancelOrderCommand {
    @TargetAggregateIdentifier
    private String orderId;
    private String reason;
    private String cancelledBy;   // BUYER | SELLER | SYSTEM
}

