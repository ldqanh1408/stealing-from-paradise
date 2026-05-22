package com.flashsale.productservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class CreateVariantRequest {

    @NotBlank
    @Size(min = 3, max = 50)
    @Pattern(regexp = "^[a-zA-Z0-9\\-]+$", message = "Variant code chỉ được chứa chữ cái, số và dấu gạch ngang")
    private String variantCode;

    @NotBlank
    @Size(min = 1, max = 100)
    private String variantName;

    @NotNull
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal price;

    private BigDecimal originalPrice;

    private Map<String, Object> variantAttributes;

    private String imageUrl;
}
