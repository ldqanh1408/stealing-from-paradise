package com.flashsale.productdomain.dto.response;

import com.flashsale.productdomain.domain.model.SkuStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkuResponse {
    private UUID id;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer stockQuantity;
    private SkuStatus status;
    private Map<String, Object> variantAttributes;
    private String imageUrl;
    private LocalDateTime priceUpdatedAt;
}
