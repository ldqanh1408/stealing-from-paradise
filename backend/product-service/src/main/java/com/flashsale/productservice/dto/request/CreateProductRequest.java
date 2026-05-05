package com.flashsale.productservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CreateProductRequest {

    @NotBlank
    @Size(min = 5, max = 200)
    private String name;

    @NotBlank
    @Size(max = 10000)
    private String description;

    @NotBlank
    private String categoryId;

    private Map<String, Object> attributes;

    @NotEmpty
    @Size(min = 1, max = 10)
    private List<String> images;
}
