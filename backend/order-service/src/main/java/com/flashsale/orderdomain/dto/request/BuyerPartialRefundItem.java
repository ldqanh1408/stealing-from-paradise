package com.flashsale.orderdomain.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BuyerPartialRefundItem {

    @NotNull(message = "order_item_id is required")
    @JsonProperty("order_item_id")
    private Long orderItemId;

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity phải >= 1")
    private Integer quantity;

    @JsonProperty("item_reason")
    private String itemReason;
}
