package com.flashsale.orderdomain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class CheckoutResponse {

    private Long parentOrderId;
    private String orderCode;
    private List<CheckoutSubOrderResponse> orders;
    private ShippingAddressInfo shippingAddress;
    private PaymentSummary payment;
    private Integer totalItems;
    private Integer totalSellers;
    private String paymentStatus;
    private Instant timeoutAt;
    private Instant createdAt;

    @Data
    @Builder
    public static class ShippingAddressInfo {
        private Long addressId;
        private String fullAddress;
        private Integer provinceId;
        private Integer districtId;
    }

    @Data
    @Builder
    public static class PaymentSummary {
        private BigDecimal totalAmount;
        private BigDecimal loyaltyDiscount;
        private Integer loyaltyPointsUsed;
        private BigDecimal finalAmount;
        private String currency;
    }
}
