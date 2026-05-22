package com.flashsale.productservice.dto.response;

import com.flashsale.productservice.domain.model.ProductVariant;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class VariantResponse {

    private String variantId;
    private String productId;
    private String variantCode;
    private String variantName;
    private Map<String, Object> variantAttributes;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer stockQuantity;
    private String status;
    private String imageUrl;
    private Integer stock;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static VariantResponse from(ProductVariant v) {
        return VariantResponse.builder()
                .variantId(v.getId())
                .productId(v.getProductId())
                .variantCode(v.getVariantCode())
                .variantName(v.getVariantName())
                .variantAttributes(v.getVariantAttributes())
                .price(v.getPrice())
                .originalPrice(v.getOriginalPrice())
                .stockQuantity(v.getStockQuantity())
                .status(v.getStatus())
                .imageUrl(v.getImageUrl())
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .build();
    }

    public static VariantResponse from(ProductVariant v, Integer stock) {
        return VariantResponse.builder()
                .variantId(v.getId())
                .productId(v.getProductId())
                .variantCode(v.getVariantCode())
                .variantName(v.getVariantName())
                .variantAttributes(v.getVariantAttributes())
                .price(v.getPrice())
                .originalPrice(v.getOriginalPrice())
                .stockQuantity(v.getStockQuantity())
                .status(v.getStatus())
                .imageUrl(v.getImageUrl())
                .stock(stock)
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .build();
    }
}
