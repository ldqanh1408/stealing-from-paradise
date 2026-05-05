package com.flashsale.productservice.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class UpdateProductRequest {

    @Size(min = 5, max = 200)
    private String name;

    @Size(max = 10000)
    private String description;

    private String categoryId;

    private Map<String, Object> attributes;

    @Size(min = 1, max = 10)
    private List<String> images;
}
