package com.flashsale.orderdomain.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CheckoutOrderItem {

    @JsonProperty("order_item_id")
    private Long orderItemId;

    @JsonProperty("sku_code")
    private String skuCode;

    @JsonProperty("product_name")
    private String productName;

    @JsonProperty("variant_name")
    private String variantName;

    @JsonProperty("image_snapshot")
    private String imageSnapshot;

    @JsonProperty("price_snapshot")
    private BigDecimal priceSnapshot;

    private Integer quantity;

    private BigDecimal subtotal;
}
