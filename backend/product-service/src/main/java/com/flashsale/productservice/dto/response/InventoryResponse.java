package com.flashsale.productservice.dto.response;

import com.flashsale.productservice.domain.model.Inventory;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryResponse {
    private String skuCode;
    private String variantCode;
    private String productId;
    private Integer stockTotal;
    private Integer stockLocked;
    private Integer stockAvailable;
    private Integer stockFlashReserved;
    private LocalDateTime lastRestockedAt;

    public static InventoryResponse from(Inventory inv) {
        return InventoryResponse.builder()
                .skuCode(inv.getSkuCode())
                .variantCode(inv.getVariantCode())
                .productId(inv.getProductId())
                .stockTotal(inv.getStockTotal())
                .stockLocked(inv.getStockLocked())
                .stockAvailable(inv.getStockAvailable())
                .stockFlashReserved(inv.getStockFlashReserved())
                .lastRestockedAt(inv.getLastRestockedAt())
                .build();
    }
}
