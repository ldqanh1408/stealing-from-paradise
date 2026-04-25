package com.flashsale.identitydomain.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LockRequest {
    private String reason;
    private java.time.LocalDateTime lockedUntil;
}
