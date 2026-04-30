package com.flashsale.orderdomain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class ParentOrderDetailResponse {

    private Long parentOrderId;
    private String orderCode;
    private Long userId;
    private BigDecimal totalAmt;
    private BigDecimal loyaltyDiscount;
    private Integer loyaltyPointsUsed;
    private BigDecimal finalAmt;
    private Long addressId;
    private Instant timeoutAt;
    private List<OrderSummaryResponse> orders;
    private Instant createdAt;
    private Instant updatedAt;
}
