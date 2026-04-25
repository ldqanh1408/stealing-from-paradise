package com.flashsale.identitydomain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointTransactionSummary {
    private Long transactionId;
    private String type;
    private Integer delta;
    private String status;
    private Long orderId;
    private String orderCode;
    private Integer balanceAfter;
    private String note;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
