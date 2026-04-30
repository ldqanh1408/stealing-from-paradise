package com.flashsale.paymentdomain.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ClientSecretResponse {

    private Long parentOrderId;
    private Long transactionId;
    private String clientSecret;
    private String status;
}
