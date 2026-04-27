package com.flashsale.productdomain.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
public class CreateProductRequest {
    @NotNull
    private UUID categoryId;

    @NotBlank
    private String name;

    @NotBlank
    private String slug;

    private String description;

    private Map<String, Object> attributes;

    @NotEmpty
    @Valid
    private List<CreateSkuRequest> skus;
}
