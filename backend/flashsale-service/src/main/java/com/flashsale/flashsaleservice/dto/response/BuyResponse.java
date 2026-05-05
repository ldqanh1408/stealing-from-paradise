package com.flashsale.flashsaleservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuyResponse {
    private Long orderId;
    private Long sessionId;
    private Long fsItemId;
    private String skuCode;
    private BigDecimal flashPrice;
    private Integer quantity;
    private BigDecimal totalAmount;
    private LocalDateTime purchasedAt;
}
