package com.flashsale.productdomain.dto.response;

import com.flashsale.productdomain.domain.model.ProductVariant;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class VariantResponse {

    private String variantId;
    private String productId;
    private String skuCode;
    private String tierName;
    private BigDecimal price;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static VariantResponse from(ProductVariant v) {
        return VariantResponse.builder()
                .variantId(v.getId())
                .productId(v.getProductId())
                .skuCode(v.getSkuCode())
                .tierName(v.getTierName())
                .price(v.getPrice())
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .build();
    }
}
