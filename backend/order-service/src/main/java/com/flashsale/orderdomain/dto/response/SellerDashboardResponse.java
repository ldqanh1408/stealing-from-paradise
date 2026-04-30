package com.flashsale.orderdomain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SellerDashboardResponse {

    private long totalProducts;
    private long ordersToday;
    private long pendingOrders;
    private BigDecimal revenueMonth;
    private double trustScore;
    private long activeProducts;
}
