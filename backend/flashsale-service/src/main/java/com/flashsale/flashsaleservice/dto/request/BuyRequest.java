package com.flashsale.flashsaleservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuyRequest {
    @NotNull
    private Long fsItemId;

    @NotNull
    @Min(1)
    private Integer quantity;

    @NotNull
    private Long addressId;
}
