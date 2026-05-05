package com.flashsale.orderservice.axon.command;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import java.util.List;

@Getter
@AllArgsConstructor
public class CreateOrderCommand {
    @TargetAggregateIdentifier
    private String orderId;
    private String buyerId;
    private String sellerId;
    private List<OrderItemDto> items;
    private Long totalAmount;
    private boolean isFlashSale;
    private String correlationId;
}

@Getter
@AllArgsConstructor
class OrderItemDto {
    private String skuCode;
    private String name;
    private Long price;
    private int quantity;
}

