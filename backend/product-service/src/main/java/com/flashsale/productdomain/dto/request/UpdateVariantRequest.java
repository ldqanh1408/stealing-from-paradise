package com.flashsale.productdomain.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateVariantRequest {

    @Size(min = 1, max = 100)
    private String tierName;

    @DecimalMin(value = "0", exclusive = true)
    private BigDecimal price;
}
