package com.flashsale.orderdomain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class OrderDetailResponse {

    private Long orderId;
    private Long parentOrderId;
    private String orderCode;
    private Long sellerId;
    private String sellerName;
    private Long buyerId;
    private String status;
    private BigDecimal totalAmt;
    private BigDecimal finalAmt;
    private Boolean isFlashSale;
    private String cancelledBy;
    private String cancelReason;
    private ShippingAddressInfo shippingAddress;
    private String trackingNumber;
    private String carrier;
    private Instant shippingDeadline;
    private String returnTrackingNumber;
    private List<OrderItemResponse> items;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @Builder
    public static class ShippingAddressInfo {
        private String fullAddress;
        private Integer provinceId;
        private Integer districtId;
    }
}
