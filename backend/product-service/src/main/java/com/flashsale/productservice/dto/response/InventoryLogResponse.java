package com.flashsale.productservice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.flashsale.productservice.domain.model.InventoryLog;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class InventoryLogResponse {

    @JsonProperty("log_id")
    private String logId;

    @JsonProperty("sku_code")
    private String skuCode;

    @JsonProperty("variant_code")
    private String variantCode;

    private Integer delta;

    private String reason;

    @JsonProperty("seller_id")
    private Long sellerId;

    private LocalDateTime timestamp;

    public static InventoryLogResponse from(InventoryLog log) {
        return InventoryLogResponse.builder()
                .logId(log.getId())
                .skuCode(log.getSkuCode())
                .variantCode(log.getVariantCode())
                .delta(log.getDelta())
                .reason(log.getReason())
                .sellerId(log.getSellerId())
                .timestamp(log.getCreatedAt())
                .build();
    }
}
