package com.flashsale.orderdomain.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Thông tin cart item nhận từ Cart Service.
 * Cart Service phải trả về các trường này qua endpoint nội bộ.
 */
@Data
public class CartItemInfo {

    @JsonProperty("cart_item_id")
    private String cartItemId;   // MongoDB ObjectId

    @JsonProperty("sku_code")
    private String skuCode;

    @JsonProperty("variant_id")
    private String variantId;

    @JsonProperty("variant_name")
    private String variantName;

    @JsonProperty("product_id")
    private String productId;

    @JsonProperty("product_name")
    private String productName;

    @JsonProperty("image_url")
    private String imageUrl;

    @JsonProperty("seller_id")
    private Long sellerId;

    @JsonProperty("seller_name")
    private String sellerName;

    @JsonProperty("price_snapshot")
    private BigDecimal priceSnapshot;

    private Integer quantity;

    @JsonProperty("fs_item_id")
    private Long fsItemId;
}
