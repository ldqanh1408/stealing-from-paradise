package com.flashsale.orderdomain.dto.response;

import com.flashsale.orderdomain.domain.model.OrderItem;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class OrderItemResponse {

    private Long orderItemId;
    private String skuCode;
    private String productName;
    private String variantName;
    private String imageSnapshot;
    private BigDecimal priceSnapshot;
    private Integer quantity;
    private Integer refundedQuantity;
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
