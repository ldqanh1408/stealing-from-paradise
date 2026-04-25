package com.flashsale.identitydomain.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventsConfigUpdateRequest {
    private Integer delta;
    private String description;
    private Boolean isActive;
}
