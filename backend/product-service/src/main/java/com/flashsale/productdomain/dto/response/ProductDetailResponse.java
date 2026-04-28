package com.flashsale.productdomain.dto.response;

import com.flashsale.productdomain.domain.model.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDetailResponse {
    private UUID id;
    private String name;
    private String description;
    private Map<String, Object> attributes;
    private ProductStatus status;
    private List<SkuResponse> skus;
    private List<ProductImageResponse> images;
    private ReviewSummaryResponse summary;
}
