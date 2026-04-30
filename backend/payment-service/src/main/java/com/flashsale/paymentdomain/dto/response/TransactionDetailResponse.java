package com.flashsale.paymentdomain.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class TransactionDetailResponse {

    private Long transactionId;
    private Long parentOrderId;
    private BigDecimal amount;
    private String method;
    private String status;
    private String stripePiId;
    private BigDecimal applicationFee;
    private BigDecimal applicationFeePercentage;
    private String transRef;
    private Instant paidAt;
    private Long remainingSeconds;
    private List<SellerTransferInfo> sellers;
}
