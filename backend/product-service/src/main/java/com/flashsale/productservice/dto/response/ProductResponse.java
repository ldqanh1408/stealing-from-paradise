package com.flashsale.productservice.dto.response;

import com.flashsale.productservice.domain.model.Product;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ProductResponse {

    private String productId;
    private Long sellerId;
    private String name;
    private String description;
    private String categoryId;
    private String categoryName;
    private String categorySlug;
    private Map<String, Object> attributes;
    private List<String> images;
    private Boolean isFlash;
    private String status;
    private String rejectReason;
    private Integer stockAvailable;
    private Long price;
    private Long originalPrice;
    private Double rating;
    private Integer reviewsCount;
    private List<VariantResponse> variants;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProductResponse from(Product p) {
        return ProductResponse.builder()
                .productId(p.getId())
                .sellerId(p.getSellerId())
                .name(p.getName())
                .description(p.getDescription())
                .categoryId(p.getCategoryId())
                .attributes(p.getAttributes())
                .images(p.getImages())
                .isFlash(p.getIsFlash())
                .status(p.getStatus())
                .rejectReason(p.getRejectReason())
                .stockAvailable(p.getStockAvailable())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
