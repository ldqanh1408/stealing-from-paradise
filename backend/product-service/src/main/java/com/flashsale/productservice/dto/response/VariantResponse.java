package com.flashsale.productservice.dto.response;

import com.flashsale.productservice.domain.model.ProductVariant;
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
    private String variantName;  // alias for tierName (frontend compatibility)
    private BigDecimal price;
    private Integer stock;       // from inventory lookup
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static VariantResponse from(ProductVariant v) {
        return VariantResponse.builder()
                .variantId(v.getId())
                .productId(v.getProductId())
                .skuCode(v.getSkuCode())
                .tierName(v.getTierName())
                .variantName(v.getTierName())  // alias
                .price(v.getPrice())
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .build();
    }

    /** Factory that also populates stock from inventory */
    public static VariantResponse from(ProductVariant v, Integer stock) {
        return VariantResponse.builder()
                .variantId(v.getId())
                .productId(v.getProductId())
                .skuCode(v.getSkuCode())
                .tierName(v.getTierName())
                .variantName(v.getTierName())
                .price(v.getPrice())
                .stock(stock)
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .build();
    }
}
