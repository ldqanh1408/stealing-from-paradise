package com.flashsale.productdomain.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
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
public class CreateSkuRequest {
    private String skuCode;

    private String variantName;

    private Map<String, Object> variantAttributes;

    @NotNull
    @Positive
    private BigDecimal price;

    private BigDecimal originalPrice;

    @NotNull
    @PositiveOrZero
    private Integer stockQuantity;

    private String status;

    private String imageUrl;
}
