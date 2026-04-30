package com.flashsale.orderdomain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class CancelOrderResponse {

    private Long orderId;
    private String orderCode;
    private String status;
    private String cancelledBy;
    private String cancelReason;
    private Instant cancelledAt;
}
