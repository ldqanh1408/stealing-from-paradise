package com.flashsale.orderdomain.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class CheckoutResponse {

    @JsonProperty("parent_order_id")
    private Long parentOrderId;

    @JsonProperty("order_code")
    private String orderCode;

    private List<CheckoutSubOrderResponse> orders;

    @JsonProperty("shipping_address")
    private ShippingAddressInfo shippingAddress;

    private PaymentSummary payment;

    @JsonProperty("total_items")
    private Integer totalItems;

    @JsonProperty("total_sellers")
    private Integer totalSellers;

    @JsonProperty("payment_status")
    private String paymentStatus;

    @JsonProperty("timeout_at")
    private Instant timeoutAt;

    @JsonProperty("created_at")
    private Instant createdAt;

    @Data
    @Builder
    public static class ShippingAddressInfo {
        @JsonProperty("address_id")
        private Long addressId;

        @JsonProperty("full_address")
        private String fullAddress;

        @JsonProperty("province_id")
        private Integer provinceId;

        @JsonProperty("district_id")
        private Integer districtId;
    }

    @Data
    @Builder
    public static class PaymentSummary {
        @JsonProperty("total_amount")
        private BigDecimal totalAmount;

        @JsonProperty("loyalty_discount")
        private BigDecimal loyaltyDiscount;

        @JsonProperty("loyalty_points_used")
        private Integer loyaltyPointsUsed;

        @JsonProperty("final_amount")
        private BigDecimal finalAmount;

        private String currency;
    }
}
