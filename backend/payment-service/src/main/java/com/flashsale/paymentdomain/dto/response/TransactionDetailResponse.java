package com.flashsale.paymentdomain.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class TransactionDetailResponse {

    @JsonProperty("transaction_id")
    private Long transactionId;

    @JsonProperty("parent_order_id")
    private Long parentOrderId;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("method")
    private String method;

    @JsonProperty("status")
    private String status;

    @JsonProperty("stripe_pi_id")
    private String stripePiId;

    @JsonProperty("application_fee")
    private BigDecimal applicationFee;

    @JsonProperty("application_fee_percentage")
    private BigDecimal applicationFeePercentage;

    @JsonProperty("trans_ref")
    private String transRef;

    @JsonProperty("paid_at")
    private Instant paidAt;

    @JsonProperty("remaining_seconds")
    private Long remainingSeconds;

    @JsonProperty("sellers")
    private List<SellerTransferInfo> sellers;
}
