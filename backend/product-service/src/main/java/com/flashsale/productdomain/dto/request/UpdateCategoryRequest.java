package com.flashsale.productdomain.dto.request;

import lombok.Data;

@Data
public class UpdateCategoryRequest {
    private String name;
    private String slug;
    private String parentId;
    private Integer level;
}
