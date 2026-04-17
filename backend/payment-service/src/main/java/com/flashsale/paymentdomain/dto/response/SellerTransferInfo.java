package com.flashsale.paymentdomain.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class SellerTransferInfo {

    @JsonProperty("seller_id")
    private Long sellerId;

    @JsonProperty("seller_name")
    private String sellerName;

    @JsonProperty("order_id")
    private Long orderId;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("fee")
    private BigDecimal fee;

    @JsonProperty("net_amount")
    private BigDecimal netAmount;

    @JsonProperty("stripe_transfer_id")
    private String stripeTransferId;

    @JsonProperty("transfer_status")
    private String transferStatus;
}
