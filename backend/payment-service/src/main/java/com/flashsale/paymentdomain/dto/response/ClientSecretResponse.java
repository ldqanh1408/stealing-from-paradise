package com.flashsale.paymentdomain.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ClientSecretResponse {

    @JsonProperty("parent_order_id")
    private Long parentOrderId;

    @JsonProperty("transaction_id")
    private Long transactionId;

    @JsonProperty("client_secret")
    private String clientSecret;

    @JsonProperty("status")
    private String status;
}
