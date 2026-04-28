package com.flashsale.productdomain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {
    private UUID id;
    private UUID productId;
    private UUID skuId;
    private UUID customerId;
    private Integer rating;
    private String title;
    private String content;
    private List<ReviewMediaResponse> media;
    private LocalDateTime createdAt;
}
