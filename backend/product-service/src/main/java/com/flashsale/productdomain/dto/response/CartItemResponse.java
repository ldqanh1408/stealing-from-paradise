package com.flashsale.productdomain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemResponse {
    private UUID id;
    private UUID cartId;
    private UUID skuId;
    private Integer quantity;
    private BigDecimal priceSnapshot;
    private String skuNameSnapshot;
    private String skuImageSnapshot;
    
    // Flags for lazy evaluation
    private Boolean hasPriceChange;
    private Boolean isUnavailable;
    private Boolean outOfStock;
    private BigDecimal currentPrice;
}
