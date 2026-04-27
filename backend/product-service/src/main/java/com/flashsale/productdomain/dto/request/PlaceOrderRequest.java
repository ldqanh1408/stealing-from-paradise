package com.flashsale.productdomain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceOrderRequest {
    @NotBlank
    private String previewToken;

    @NotEmpty
    private List<UUID> cartItemIds;

    private UUID orderId;

    private String paymentMethod;

    private String addressId;
}
