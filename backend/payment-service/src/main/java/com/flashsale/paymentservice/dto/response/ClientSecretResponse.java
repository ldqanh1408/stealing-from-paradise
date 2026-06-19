package com.flashsale.paymentservice.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ClientSecretResponse {
    private String clientSecret;
    private Long parentOrderId;
    private Long transactionId;
    private BigDecimal amount;
    private String currency;
    private List<PaymentIntentItem> paymentIntents;

    @Data
    @Builder
    public static class PaymentIntentItem {
        private String clientSecret;
        private String stripeAccountId;
        private Long sellerId;
        private BigDecimal amount;
    }
}
