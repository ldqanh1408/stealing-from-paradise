package com.flashsale.productdomain.dto.response;

import com.flashsale.productdomain.domain.model.ReviewMediaType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewMediaResponse {
    private UUID id;
    private UUID reviewId;
    private String url;
    private ReviewMediaType mediaType;
    private Integer sortOrder;
}
