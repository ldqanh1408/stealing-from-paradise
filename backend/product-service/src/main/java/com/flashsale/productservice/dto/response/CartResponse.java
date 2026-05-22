package com.flashsale.productservice.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartResponse {
    private String cartId;
    private List<CartSellerGroup> sellers;
    private Integer totalItems;
    private BigDecimal subtotal;
    private Boolean hasWarning;
    private List<String> warnings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartSellerGroup {
        private Long sellerId;
        private String sellerName;
        private List<CartItemResponse> items;
        private BigDecimal sellerSubtotal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartItemResponse {
        private String cartItemId;
        private String skuCode;
        private String productId;
        private String productName;
        private String variantId;
        private String variantName;
        private String variantImage;
        private BigDecimal unitPrice;
        private BigDecimal currentPrice;
        private Boolean priceChanged;
        private Integer quantity;
        private Integer stockAvailable;
        private String stockStatus;
        private Boolean isFlashSale;
        private Long fsItemId;
        private BigDecimal subtotal;
        private LocalDateTime addedAt;
    }
}
