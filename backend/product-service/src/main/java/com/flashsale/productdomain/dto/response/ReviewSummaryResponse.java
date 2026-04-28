package com.flashsale.productdomain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSummaryResponse {
    private Double avgRating;
    private Integer totalCount;
    private Integer count5star;
    private Integer count4star;
    private Integer count3star;
    private Integer count2star;
    private Integer count1star;
    private Integer countWithMedia;
}
