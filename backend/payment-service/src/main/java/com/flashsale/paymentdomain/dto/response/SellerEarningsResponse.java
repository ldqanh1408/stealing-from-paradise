package com.flashsale.paymentdomain.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class SellerEarningsResponse {

    @JsonProperty("total_earnings")
    private BigDecimal totalEarnings;

    @JsonProperty("available_balance")
    private BigDecimal availableBalance;

    @JsonProperty("pending_balance")
    private BigDecimal pendingBalance;

    @JsonProperty("platform_fee_percentage")
    private BigDecimal platformFeePercentage;

    @JsonProperty("total_orders")
    private Long totalOrders;

    @JsonProperty("transfers")
    private List<SellerTransferItem> transfers;

    @Data
    @Builder
    public static class SellerTransferItem {

        @JsonProperty("id")
        private Long id;

        @JsonProperty("order_id")
        private Long orderId;

        @JsonProperty("order_code")
        private String orderCode;

        @JsonProperty("transfer_amount")
        private BigDecimal transferAmount;

        @JsonProperty("fee_amount")
        private BigDecimal feeAmount;

        @JsonProperty("net_amount")
        private BigDecimal netAmount;

        @JsonProperty("stripe_transfer_id")
        private String stripeTransferId;

        @JsonProperty("status")
        private String status;

        @JsonProperty("created_at")
        private String createdAt;

        @JsonProperty("updated_at")
        private String updatedAt;
    }
}
