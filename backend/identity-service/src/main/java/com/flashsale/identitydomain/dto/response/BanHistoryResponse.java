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
public class BanHistoryResponse {
    private Long id;
    private String action;
    private String reason;
    private String performedBy;
    private Long adminId;
    private LocalDateTime lockedUntil;
    private LocalDateTime createdAt;
}
