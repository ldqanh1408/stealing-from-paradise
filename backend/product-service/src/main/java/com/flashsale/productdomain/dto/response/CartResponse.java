package com.flashsale.productdomain.dto.response;

import com.flashsale.productdomain.domain.model.CartStatus;
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
public class CartResponse {
    private UUID id;
    private Long customerId;
    private CartStatus status;
    private List<CartItemResponse> items;
}
