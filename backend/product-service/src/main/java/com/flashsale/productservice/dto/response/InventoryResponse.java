package com.flashsale.productservice.dto.response;

import com.flashsale.productservice.domain.model.Inventory;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InventoryResponse {

    private String skuCode;
    private Integer stockTotal;
    private Integer stockLocked;
    private Integer stockAvailable;
    private Integer stockFlashReserved;

    public static InventoryResponse from(Inventory inv) {
        return InventoryResponse.builder()
                .skuCode(inv.getSkuCode())
                .stockTotal(inv.getStockTotal())
                .stockLocked(inv.getStockLocked())
                .stockAvailable(inv.getStockAvailable())
                .stockFlashReserved(inv.getStockFlashReserved())
                .build();
    }
}
