package com.flashsale.paymentdomain.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class SellerTransferInfo {

    private Long sellerId;
    private String sellerName;
    private Long orderId;
    private BigDecimal amount;
    private BigDecimal fee;
    private BigDecimal netAmount;
    private String stripeTransferId;
    private String transferStatus;
}
