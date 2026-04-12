package com.flashsale.paymentdomain.axon.query;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FindPaymentStatusQuery {
    private String orderId;
}

