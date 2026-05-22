package com.flashsale.productservice.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartRequest {

    @NotBlank(message = "sku_code là bắt buộc")
    private String skuCode;

    @NotNull(message = "quantity là bắt buộc")
    @Min(value = 1, message = "Số lượng tối thiểu là 1")
    @Max(value = 1000, message = "Số lượng tối đa là 1000")
    private Integer quantity;

    private Long fsItemId;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class UpdateCartItemRequest {
    @NotNull(message = "quantity là bắt buộc")
    @Min(value = 0, message = "Số lượng không thể âm")
    @Max(value = 1000, message = "Số lượng tối đa là 1000")
    private Integer quantity;
}
