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
public class AppealInfoResponse {
    private Long appealId;
    private Long userId;
    private Long trustScoreLogId;
    private String reason;
    private String evidenceUrls;
    private String status;
    private Long reviewedBy;
    private String adminNote;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}
