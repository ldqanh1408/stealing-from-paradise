package com.flashsale.identitydomain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoyaltyEstimateResponse {
    private Long orderAmount;
    private Integer pointsToEarn;
    private String pointsToEarnFormula;
    private Integer availablePoints;
    private Integer maxPointsUsable;
    private String maxPointsUsableFormula;
    private Integer conversionRate;
    private Integer pointsRequested;
    private Long discountIfUse50;
    private Integer capPercent;
}
