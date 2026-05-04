package com.flashsale.identitydomain.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrustScoreAdjustRequest {
    private Integer delta;
    private String reason;
}
