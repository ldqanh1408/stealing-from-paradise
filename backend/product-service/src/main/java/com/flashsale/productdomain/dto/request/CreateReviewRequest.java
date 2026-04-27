package com.flashsale.productdomain.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReviewRequest {
    @NotNull
    private UUID orderItemId;

    private UUID skuId;

    @NotNull
    @Min(1)
    @Max(5)
    private Integer rating;

    private String title;

    private String content;

    @Valid
    private List<CreateReviewMediaRequest> media;
}
