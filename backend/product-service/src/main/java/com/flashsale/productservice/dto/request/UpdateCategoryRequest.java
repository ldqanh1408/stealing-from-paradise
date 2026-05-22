package com.flashsale.productservice.dto.request;

import lombok.Data;

@Data
public class UpdateCategoryRequest {
    private String name;
    private String slug;
    private String parentId;
    private Integer level;
    private String description;
    private String imageUrl;
    private Boolean isActive;
    private Integer sortOrder;
}
