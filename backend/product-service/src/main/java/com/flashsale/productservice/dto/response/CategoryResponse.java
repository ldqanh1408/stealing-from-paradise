package com.flashsale.productservice.dto.response;

import com.flashsale.productservice.domain.model.Category;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {
    private String categoryId;
    private String name;
    private String slug;
    private String parentId;
    private String description;
    private String imageUrl;
    private Boolean isActive;
    private Integer sortOrder;
    private Integer level;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<CategoryResponse> children;

    public static CategoryResponse from(Category c) {
        return CategoryResponse.builder()
                .categoryId(c.getId())
                .name(c.getName())
                .slug(c.getSlug())
                .parentId(c.getParentId())
                .description(c.getDescription())
                .imageUrl(c.getImageUrl())
                .isActive(c.getIsActive())
                .sortOrder(c.getSortOrder())
                .level(c.getLevel())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
