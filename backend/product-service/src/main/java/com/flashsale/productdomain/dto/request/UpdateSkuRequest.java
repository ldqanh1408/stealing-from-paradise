package com.flashsale.productdomain.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSkuRequest {
    private String skuCode;

    private String variantName;

    private Map<String, Object> variantAttributes;

    private BigDecimal price;

    private BigDecimal originalPrice;

    private Integer stockQuantity;

    private String status;

    private String imageUrl;
}
