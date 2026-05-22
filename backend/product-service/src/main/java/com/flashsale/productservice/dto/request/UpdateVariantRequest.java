package com.flashsale.productservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class UpdateVariantRequest {

    @Size(min = 1, max = 100)
    private String variantName;

    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal price;

    private BigDecimal originalPrice;

    private Map<String, Object> variantAttributes;

    private String imageUrl;
}
