package com.flashsale.orderdomain.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class OrderDetailResponse {

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

    @JsonProperty("buyer_id")
    private Long buyerId;

    private String status;

    @JsonProperty("total_amt")
    private BigDecimal totalAmt;

    @JsonProperty("final_amt")
    private BigDecimal finalAmt;

    @JsonProperty("is_flash_sale")
    private Boolean isFlashSale;

    @JsonProperty("cancelled_by")
    private String cancelledBy;

    @JsonProperty("cancel_reason")
    private String cancelReason;

    @JsonProperty("shipping_address")
    private ShippingAddressInfo shippingAddress;

    @JsonProperty("tracking_number")
    private String trackingNumber;

    private String carrier;

    @JsonProperty("shipping_deadline")
    private Instant shippingDeadline;

    @JsonProperty("return_tracking_number")
    private String returnTrackingNumber;

    private List<OrderItemResponse> items;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    @Data
    @Builder
    public static class ShippingAddressInfo {
        @JsonProperty("full_address")
        private String fullAddress;

        @JsonProperty("province_id")
        private Integer provinceId;

        @JsonProperty("district_id")
        private Integer districtId;
    }
}
