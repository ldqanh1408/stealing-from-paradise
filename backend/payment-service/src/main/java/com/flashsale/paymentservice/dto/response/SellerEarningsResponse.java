package com.flashsale.paymentservice.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class SellerEarningsResponse {

    private BigDecimal totalEarnings;
    private BigDecimal availableBalance;
    private BigDecimal pendingBalance;
    private BigDecimal platformFeePercentage;
    private Long totalOrders;
    private List<SellerTransferItem> transfers;

    @Data
    @Builder
    public static class SellerTransferItem {

        private Long id;
        private Long orderId;
        private String orderCode;
        private BigDecimal transferAmount;
        private BigDecimal feeAmount;
        private BigDecimal netAmount;
        private String stripeTransferId;
        private String status;
        private String deliveredAt;
        private String payoutEligibleAt;
        private String payoutAt;
        private String createdAt;
        private String updatedAt;
    }
}
