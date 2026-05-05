package com.flashsale.productservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InventoryAdjustRequest {

    @NotBlank
    private String skuCode;

    @NotNull
    private Integer delta;

    @NotBlank
    private String reason;
}
