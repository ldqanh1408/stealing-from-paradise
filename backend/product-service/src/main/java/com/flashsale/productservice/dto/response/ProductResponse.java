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
    private LocalDateTime reviewedAt;
    private Long reviewedBy;
    private Integer rejectCount;
    private String slug;
    private Integer totalStock;
    private Long minPrice;
    private Long maxPrice;
    private Boolean hasDiscount;
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
                .isFlash(p.getIsFlashSale())
                .status(p.getStatus())
                .rejectReason(p.getRejectReason())
                .reviewedAt(p.getReviewedAt())
                .reviewedBy(p.getReviewedBy())
                .rejectCount(p.getRejectCount())
                .slug(p.getSlug())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
