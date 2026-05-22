package com.flashsale.productservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateCategoryRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String slug;

    private String parentId;

    @NotNull
    private Integer level;

    private String description;

    private String imageUrl;

    private Integer sortOrder;
}
