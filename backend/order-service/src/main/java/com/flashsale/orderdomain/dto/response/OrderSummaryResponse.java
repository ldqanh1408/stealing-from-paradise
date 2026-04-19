package com.flashsale.orderdomain.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flashsale.orderdomain.domain.model.Order;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;

@Data
@Builder
public class OrderSummaryResponse {

    @JsonProperty("order_id")
    private Long orderId;

    @JsonProperty("parent_order_id")
    private Long parentOrderId;

    @JsonProperty("order_code")
    private String orderCode;

    @JsonProperty("seller_id")
    private Long sellerId;

    @JsonProperty("seller_name")
    private String sellerName;

    private String status;

    @JsonProperty("total_amt")
    private BigDecimal totalAmt;

    @JsonProperty("final_amt")
    private BigDecimal finalAmt;

    @JsonProperty("is_flash_sale")
    private Boolean isFlashSale;

    @JsonProperty("item_count")
    private Integer itemCount;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    public static OrderSummaryResponse from(Order order) {
        return OrderSummaryResponse.builder()
                .orderId(order.getId())
                .parentOrderId(order.getParentOrderId())
                .orderCode(order.getOrderCode())
                .sellerId(order.getSellerId())
                .sellerName(order.getSellerName())
                .status(order.getStatus())
                .totalAmt(order.getTotalAmt())
                .finalAmt(order.getFinalAmt())
                .isFlashSale(order.getIsFlashSale())
                .itemCount(order.getItems() != null ? order.getItems().size() : 0)
                .createdAt(order.getCreatedAt() != null ? order.getCreatedAt().toInstant(ZoneOffset.UTC) : null)
                .updatedAt(order.getUpdatedAt() != null ? order.getUpdatedAt().toInstant(ZoneOffset.UTC) : null)
                .build();
    }
}
