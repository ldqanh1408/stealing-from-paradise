package com.flashsale.orderdomain.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flashsale.orderdomain.domain.model.OrderItem;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class OrderItemResponse {

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

    @JsonProperty("refunded_quantity")
    private Integer refundedQuantity;

    @JsonProperty("fs_item_id")
    private Long fsItemId;

    public static OrderItemResponse from(OrderItem item) {
        return OrderItemResponse.builder()
                .orderItemId(item.getId())
                .skuCode(item.getSkuCode())
                .productName(item.getNameSnapshot())
                .variantName(item.getVariantName())
                .imageSnapshot(item.getImageSnapshot())
                .priceSnapshot(item.getPriceSnapshot())
                .quantity(item.getQuantity())
                .refundedQuantity(item.getRefundedQuantity())
                .fsItemId(item.getFsItemId())
                .build();
    }
}
