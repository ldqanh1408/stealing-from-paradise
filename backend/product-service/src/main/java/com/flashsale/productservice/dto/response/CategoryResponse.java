package com.flashsale.productservice.dto.response;

import com.flashsale.productservice.domain.model.Category;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CategoryResponse {

    private String categoryId;
    private String name;
    private String slug;
    private String parentId;
    private Integer level;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CategoryResponse from(Category c) {
        return CategoryResponse.builder()
                .categoryId(c.getId())
                .name(c.getName())
                .slug(c.getSlug())
                .parentId(c.getParentId())
                .level(c.getLevel())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
