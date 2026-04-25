package com.flashsale.identitydomain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrustScoreLogResponse {
    private Long logId;
    private String eventCode;
    private Integer delta;
    private Integer scoreAfter;
    private String changedBy;
    private String reason;
    private LocalDateTime createdAt;
}
