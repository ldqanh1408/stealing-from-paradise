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
public class EventsConfigResponse {
    private Long id;
    private String eventCode;
    private Integer delta;
    private String description;
    private Boolean isActive;
    private LocalDateTime updatedAt;
}
