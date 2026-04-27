package com.flashsale.productdomain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductImageRequest {
    @NotBlank
    private String url;

    private UUID skuId;

    private Integer sortOrder;
}
